package no.nav.hjelpemidler.suggestions

import mu.KotlinLogging
import no.nav.hjelpemidler.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.denyList
import no.nav.hjelpemidler.github.GithubClient
import no.nav.hjelpemidler.github.Hmsnr
import no.nav.hjelpemidler.github.Delelister
import no.nav.hjelpemidler.metrics.AivenMetrics
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
    private val aivenMetrics: AivenMetrics,
    private val hjelpemiddeldatabaseClient: HjelpemiddeldatabaseClient,
    private val githubClient: GithubClient,
    private val oebs: Oebs,
) {

    suspend fun suggestions(hmsnr: String): SuggestionsFrontendFiltered {
        val hovedprodukt = hjelpemiddeldatabaseClient.hentProdukter(hmsnr).first()
        val forslag = store.suggestions(hmsnr)

        val tilbehørslister = githubClient.hentTilbehørslister()

        val (forslagPåRammeAvtale, forslagIkkePåRammeavtale) = forslag.suggestions
            .partition {
                hmsnrFinnesPåDelelisteForHovedprodukt(
                    it.hmsNr,
                    tilbehørslister,
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

        val tilbehør = hjelpemiddelTilbehørIBestillingsliste.map { it to hentTilbehør(it, null) }
            .filter { (_, nameLookup) ->
                nameLookup.name != null && nameLookup.error == null
            }
            .map { (hmsnr, nameLookup) ->
                SuggestionFrontendFiltered(
                    hmsNr = hmsnr,
                    title = nameLookup.name!!
                )
            }

        return SuggestionsFrontendFiltered(LocalDate.now(), tilbehør)
    }

    suspend fun hentTilbehør(hmsnr: String, hmsnrHovedprodukt: String?): Tilbehør {
        try {
            if (hmsnrHovedprodukt != null) {
                val hovedprodukt = hjelpemiddeldatabaseClient.hentProdukter(hmsnrHovedprodukt).first()
                val reservedelslister = githubClient.hentReservedelslister()
                val tilbehørslister = githubClient.hentTilbehørslister()
                val hmsnrFinnesITilbehørsliste =
                    hmsnrFinnesPåDelelisteForHovedprodukt(hmsnr, tilbehørslister, hovedprodukt)
                val hmsnrFinnesIReservedelsliste =
                    hmsnrFinnesPåDelelisteForHovedprodukt(hmsnr, reservedelslister, hovedprodukt)

                logg.info { "reservedelsjekk: hmsnr <$hmsnr>, hovedprodukt <$hmsnrHovedprodukt>, hmsnrFinnesITilbehørsliste <$hmsnrFinnesITilbehørsliste>, hmsnrFinnesIReservedelsliste <$hmsnrFinnesIReservedelsliste>" }
                if (hmsnrFinnesIReservedelsliste && !hmsnrFinnesITilbehørsliste) {
                    logg.info { "hmsnr <$hmsnr> finnes på reservedelsliste, men ikke i tilbehørsliste" }
                    aivenMetrics.hmsnrErReservedel(hmsnr)
                    // return Tilbehør(hmsnr, null, TilbehørError.RESERVEDEL)
                }
            }
        } catch (e: Exception) {
            // Logger feilen, men går videre uten å gjøre noe. Saksbehandler kan evt. saksbehandle dersom hmsnr er standardutstyr
            logg.error(e) { "Sjekk om hmsnr er reservedel feilet for hmsnr <$hmsnr>." }
        }


        runCatching {
            // Søknaden er avhengig av denne gamle sjekken, da den egentlig sjekker om produktet eksisterer i hmdb
            // og hvis så om den er tilgjengelig for å legges til igjennom digital søknad som hovedprodukt.
            var feilmelding: TilbehørError? = null
            val hmdbResults = hjelpemiddeldatabaseClient.hentProdukter(hmsnr)
            if (hmdbResults.any { it.tilgjengeligForDigitalSoknad }) {
                logg.info("DEBUG: product looked up with /lookup-accessory-name was not really an accessory")
                feilmelding = TilbehørError.IKKE_ET_TILBEHØR // men tilgjengelig som hovedprodukt
            } else if (hmdbResults.any { it.produkttype == Produkttype.HOVEDPRODUKT }) {
                feilmelding = TilbehørError.IKKE_TILGJENGELIG_DIGITALT // hovedprodukt som må søkes på papir
            } else if (hmsnr in denyList) {
                feilmelding = TilbehørError.IKKE_TILGJENGELIG_DIGITALT
            }

            val delnavn = hentDelnavn(hmsnr) ?: return Tilbehør(hmsnr, null, TilbehørError.IKKE_FUNNET)

            return Tilbehør(hmsnr, delnavn, feilmelding)
        }.getOrElse { e ->
            logg.error(e) { "failed to find title for hmsNr=$hmsnr" }
            return Tilbehør(hmsnr, null, TilbehørError.IKKE_FUNNET)
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
            val hmsNrsSkipList = hjelpemiddeldatabaseClient
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

    private fun hentDelnavn(hmsnr: String): String? {
        val titleFromSuggestionEngineCache = store.cachedTitleAndTypeFor(hmsnr)
        var oebsTitleAndType: Pair<String, String>? = null
        if (titleFromSuggestionEngineCache?.title == null) {
            oebsTitleAndType = oebs.getTitleForHmsNr(hmsnr)
            logg.info("DEBUG: Fetched title for $hmsnr and oebs report it as having type: ${oebsTitleAndType.second}. Title: ${oebsTitleAndType.first}")
            if (oebsTitleAndType.second != "Del") {
                logg.info("DEBUG: $hmsnr is not a \"DEl\" according to OEBS (type=${oebsTitleAndType.second}; title=${oebsTitleAndType.first})")
                // accessory = false
            }
        } else {
            logg.info("DEBUG: Using cached oebs title from suggestion engine for $hmsnr: $titleFromSuggestionEngineCache")
        }

        return oebsTitleAndType?.first ?: titleFromSuggestionEngineCache?.title
    }
}

data class Tilbehør(
    val hmsnr: String,
    val name: String?,
    val error: TilbehørError?,
)

enum class TilbehørError() {
    RESERVEDEL,
    IKKE_FUNNET,
    IKKE_ET_TILBEHØR,
    IKKE_TILGJENGELIG_DIGITALT,
}

private fun hmsnrFinnesPåDelelisteForHovedprodukt(
    hmsnr: Hmsnr,
    delelister: Delelister,
    hovedprodukt: Produkt,
): Boolean =
    delelister[hovedprodukt.rammeavtaleId]?.get(hovedprodukt.leverandorId)?.contains(hmsnr) ?: false

