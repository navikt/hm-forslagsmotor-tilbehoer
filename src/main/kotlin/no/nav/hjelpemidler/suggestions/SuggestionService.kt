package no.nav.hjelpemidler.suggestions

import mu.KotlinLogging
import no.nav.hjelpemidler.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.denyList
import no.nav.hjelpemidler.github.CachedGithubClient
import no.nav.hjelpemidler.github.GithubClient
import no.nav.hjelpemidler.github.Hmsnr
import no.nav.hjelpemidler.github.Rammeavtaler
import no.nav.hjelpemidler.model.ProductFrontendFiltered
import no.nav.hjelpemidler.model.SuggestionFrontendFiltered
import no.nav.hjelpemidler.model.SuggestionsFrontendFiltered
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.service.hmdb.enums.Produkttype
import no.nav.hjelpemidler.service.hmdb.hentprodukter.Produkt
import java.time.LocalDate
import kotlin.system.measureTimeMillis

private val logg = KotlinLogging.logger { }

class SuggestionService(
    private val store: SuggestionEngine,
    private val githubClient: GithubClient = CachedGithubClient()
) {

    suspend fun suggestions(hmsnr: String): SuggestionsFrontendFiltered {
        val hovedprodukt = HjelpemiddeldatabaseClient.hentProdukter(hmsnr).first()
        val forslag = store.suggestions(hmsnr)

        val rammeavtaleTilbehør = githubClient.hentRammeavtalerForTilbehør()

        val (forslagPåRammeAvtale, forslagIkkePåRammeavtale) = forslag.suggestions
            .partition {
                tilbehørErPåRammeavtalenTilHovedprodukt(
                    tilbehør = it.hmsNr,
                    rammeavtaleTilbehør,
                    hovedprodukt
                ) && it.hmsNr !in denyList
            }

        val results = SuggestionsFrontendFiltered(
            forslag.dataStartDate,
            forslagPåRammeAvtale.map { it.toFrontendFiltered() }
        )

        logg.info { "Forslagresultat: hmsnr <$hmsnr>, forslag <$forslag>, forslagPåRammeAvtale <$forslagPåRammeAvtale>, forslagIkkePåRammeavtale <$forslagIkkePåRammeavtale>, results <$results>" }

        // Sletter fra db slik at de ikke tar opp plassen til andre forslag i fremtiden
        store.deleteSuggestions(forslagIkkePåRammeavtale.map { it.hmsNr })

        return results
    }

    suspend fun hentBestillingstilbehør(hmsnr: String): SuggestionsFrontendFiltered {
        val bestillingsOrdningSortiment = githubClient.hentBestillingsordningSortiment()

        val hjelpemiddelTilbehørIBestillingsliste = bestillingsOrdningSortiment.find { it.hmsnr == hmsnr }?.tilbehor

        if (hjelpemiddelTilbehørIBestillingsliste.isNullOrEmpty()) {
            logg.info { "Fant ingen tilbehør for $hmsnr i bestillingsordningsortimentet" }
            return SuggestionsFrontendFiltered(LocalDate.now(), emptyList())
        }

        logg.info("Fant tilbehør <$hjelpemiddelTilbehørIBestillingsliste> for $hmsnr i bestillingsordningsortimentet")

        val tilbehør = hjelpemiddelTilbehørIBestillingsliste.map { it to hentTilbehørNavn(it) }
            .filter { (_, nameLookup) ->
                nameLookup.name != null && nameLookup.error.isNullOrEmpty()
            }
            .map { (hmsnr, nameLookup) ->
                SuggestionFrontendFiltered(
                    hmsNr = hmsnr,
                    title = nameLookup.name!!
                )
            }

        return SuggestionsFrontendFiltered(LocalDate.now(), tilbehør)
    }

    suspend fun hentTilbehørNavn(hmsnr: String): LookupAccessoryName {
        runCatching {
            // Søknaden er avhengig av denne gamle sjekken, da den egentlig sjekker om produktet eksisterer i hmdb
            // og hvis så om den er tilgjengelig for å legges til igjennom digital søknad som hovedprodukt.
            var feilmelding: String? = null
            val hmdbResults = HjelpemiddeldatabaseClient.hentProdukter(hmsnr)
            if (hmdbResults.any { it.tilgjengeligForDigitalSoknad }) {
                logg.info("DEBUG: product looked up with /lookup-accessory-name was not really an accessory")
                feilmelding = "ikke et tilbehør" // men tilgjengelig som hovedprodukt
            } else if (hmdbResults.any { it.produkttype == Produkttype.HOVEDPRODUKT }) {
                feilmelding = "ikke tilgjengelig digitalt" // hovedprodukt som må søkes på papir
            } else if (hmsnr in denyList) {
                feilmelding = "ikke tilgjengelig digitalt"
            }

            val titleFromSuggestionEngineCache = store.cachedTitleAndTypeFor(hmsnr)
            var oebsTitleAndType: Pair<String, String>? = null
            if (titleFromSuggestionEngineCache?.title == null) {
                oebsTitleAndType = Oebs.getTitleForHmsNr(hmsnr)
                logg.info("DEBUG: Fetched title for $hmsnr and oebs report it as having type: ${oebsTitleAndType.second}. Title: ${oebsTitleAndType.first}")
                if (oebsTitleAndType.second != "Del") {
                    logg.info("DEBUG: $hmsnr is not a \"DEl\" according to OEBS (type=${oebsTitleAndType.second}; title=${oebsTitleAndType.first})")
                    // accessory = false
                }
            } else {
                logg.info("DEBUG: Using cached oebs title from suggestion engine for $hmsnr: $titleFromSuggestionEngineCache")
            }

            return LookupAccessoryName(
                oebsTitleAndType?.first ?: titleFromSuggestionEngineCache?.title,
                feilmelding
            )
        }.getOrElse { e ->
            logg.error(e) { "failed to find title for hmsNr=$hmsnr" }
            return LookupAccessoryName(null, "produkt ikke funnet")
        }
    }

    suspend fun introspect(): List<ProductFrontendFiltered>? {
        var result: List<ProductFrontendFiltered>?
        val timeElapsed = measureTimeMillis {
            result = store.introspect()

            // Filter out a distinct list of hmsnrs for accessories that could be suggested by the suggestion engine
            val allSuggestionHmsnrs = result!!
                .map { it.suggestions }
                .fold(mutableListOf<String>()) { a, b ->
                    a.addAll(b.map { it.hmsNr })
                    a
                }.toSet()

            // Talk to hm-grunndata about a skip list
            val hmsNrsSkipList = HjelpemiddeldatabaseClient
                .hentProdukter(allSuggestionHmsnrs)
                .filter { it.hmsnr != null && (it.tilgjengeligForDigitalSoknad || it.produkttype == Produkttype.HOVEDPRODUKT) }
                .map { it.hmsnr!! }

            // Filter out illegal suggestions because they are not accessories (they can be applied to digitally as products)
            result = result!!.map {
                ProductFrontendFiltered(
                    hmsnr = it.hmsnr,
                    title = it.title,
                    frameworkAgreementStartDate = it.frameworkAgreementStartDate,
                    suggestions = it.suggestions.filter { !hmsNrsSkipList.contains(it.hmsNr) },
                )
            }

            // Don't include introspection results with all suggestions fitltered out by grunndata-above.
            result = result!!.filter { it.suggestions.isNotEmpty() }
        }

        logg.info("Request for introspection of suggestions (timeElapsed=${timeElapsed}ms)")
        return result
    }
}

data class LookupAccessoryName(
    val name: String?,
    val error: String?,
)

private fun tilbehørErPåRammeavtalenTilHovedprodukt(
    tilbehør: Hmsnr,
    rammeavtaler: Rammeavtaler,
    hovedprodukt: Produkt,
): Boolean =
    rammeavtaler[hovedprodukt.rammeavtaleId]?.get(hovedprodukt.leverandorId)?.contains(tilbehør) ?: false

