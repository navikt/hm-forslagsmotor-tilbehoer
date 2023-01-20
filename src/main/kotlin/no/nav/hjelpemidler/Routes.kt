package no.nav.hjelpemidler

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.get
import mu.KotlinLogging
import no.nav.hjelpemidler.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.github.Github
import no.nav.hjelpemidler.model.ProductFrontendFiltered
import no.nav.hjelpemidler.model.Suggestion
import no.nav.hjelpemidler.model.Suggestions
import no.nav.hjelpemidler.model.SuggestionsFrontendFiltered
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.service.hmdb.enums.Produkttype
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine
import java.time.LocalDate
import kotlin.system.measureTimeMillis

private val logg = KotlinLogging.logger {}

fun Route.ktorRoutes(store: SuggestionEngine) {
    // authenticate("tokenX", "aad") {}

    get("/suggestions/{hmsNr}") {
        val hmsnr = call.parameters["hmsnr"]!!

        logg.info("Request for suggestions for hmsnr=$hmsnr.")
        val suggestions = store.suggestions(hmsnr)

        val hmsNrsSkipList = HjelpemiddeldatabaseClient
            .hentProdukter(suggestions.suggestions.map { it.hmsNr }.toSet())
            .filter { it.hmsnr != null && (it.tilgjengeligForDigitalSoknad || it.produkttype == Produkttype.HOVEDPRODUKT) }
            .map { it.hmsnr!! }

        val results = SuggestionsFrontendFiltered(
            suggestions.dataStartDate,
            suggestions.suggestions
                .filter { !hmsNrsSkipList.contains(it.hmsNr) }
                .map { it.toFrontendFiltered() },
        )

        call.respond(results)

        // Sletter fra db slik at de ikke tar opp plassen til andre forslag i fremtiden
        store.deleteSuggestions(hmsNrsSkipList)
    }

    get("/tilbehoer/bestilling/{hmsNr}") {
        val hmsnr = call.parameters["hmsnr"]!!
        logg.info("Request for tilbehor bestilling for hmsnr=$hmsnr.")

        val bestillingsOrdningSortiment = Github.hentBestillingsordningSortiment()

        val hjelpemiddelTilbehørIBestillingsliste = bestillingsOrdningSortiment.find { it.hmsnr == hmsnr }?.tilbehor

        if (hjelpemiddelTilbehørIBestillingsliste.isNullOrEmpty()) {
            logg.info { "Fant ingen tilbehør for $hmsnr i bestillingsordningsortimentet" }
            return@get call.respond(emptyList<Suggestion>())
        }

        logg.info("Fant tilbehør <$hjelpemiddelTilbehørIBestillingsliste> for $hmsnr i bestillingsordningsortimentet")

        val produkter = HjelpemiddeldatabaseClient.hentProdukter(hjelpemiddelTilbehørIBestillingsliste.toSet())
        logg.info { "Hentet produkter <$produkter> som tilbehør for hmsnr <$hmsnr>" }

        val suggestions = produkter
            .filter { it.hmsnr != null && !(it.tilgjengeligForDigitalSoknad || it.produkttype == Produkttype.HOVEDPRODUKT) }
            .map { Suggestion(hmsNr = it.hmsnr!!, title = it.artikkelnavn) }

        val response = Suggestions(LocalDate.now(), suggestions)

        logg.info { "Returnerer response <$response> for bestilling tilbehøroppslag på hmsnr <$hmsnr>" }

        call.respond(response)
    }

    get("/lookup-accessory-name/{hmsNr}") {
        val hmsnr = call.parameters["hmsNr"]!!

        data class LookupAccessoryName(
            val name: String?,
            val error: String?,
        )

        logg.info("Request for name lookup for hmsnr=$hmsnr.")
        runCatching {
            // Søknaden er avhengig av denne gamle sjekken, da den egentlig sjekker om produktet eksisterer i hmdb
            // og hvis så om den er tilgjengelig for å legges til igjennom digital søknad som hovedprodukt.
            var feilmelding: String? = null
            val hmdbResults = HjelpemiddeldatabaseClient.hentProdukter(hmsnr)
            if (hmdbResults.any { it.tilgjengeligForDigitalSoknad }) {
                logg.info("DEBUG: product looked up with /lookup-accessory-name was not really an accessory")
                feilmelding = "ikke et tilbehør" // men tilgjengelig som hovedprodukt
            } else if (hmdbResults.any { it.produkttype == Produkttype.HOVEDPRODUKT }) {
                feilmelding = "ikke tilgjengelig digitalt" // hovedprodukt, men ikke tilgjengelig digitalt
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

            call.respond(
                LookupAccessoryName(
                    oebsTitleAndType?.first ?: titleFromSuggestionEngineCache?.title,
                    feilmelding
                )
            )
        }.getOrElse { e ->
            logg.info("warn: failed to find title for hmsNr=$hmsnr")
            e.printStackTrace()
            call.respond(LookupAccessoryName(null, "produkt ikke funnet"))
        }
    }

    get("/introspect") {
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
        call.respond(result!!)
    }

    get("/inspection") { call.respondRedirect("/introspect") }
}
