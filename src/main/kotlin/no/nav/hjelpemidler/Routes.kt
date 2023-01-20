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
import no.nav.hjelpemidler.suggestions.SuggestionService
import java.time.LocalDate
import kotlin.system.measureTimeMillis

private val logg = KotlinLogging.logger {}

fun Route.ktorRoutes(suggestionService: SuggestionService) {

    get("/suggestions/{hmsNr}") {
        val hmsnr = call.parameters["hmsnr"]!!
        logg.info("Request for suggestions for hmsnr=$hmsnr.")
        call.respond(suggestionService.suggestions(hmsnr))
    }

    get("/tilbehoer/bestilling/{hmsNr}") {
        val hmsnr = call.parameters["hmsnr"]!!
        logg.info("Request for tilbehor bestilling for hmsnr=$hmsnr.")
        val response = suggestionService.hentBestillingstilbeør(hmsnr)
        logg.info { "Returnerer response <$response> for bestilling tilbehøroppslag på hmsnr <$hmsnr>" }
        call.respond(response)
    }

    get("/lookup-accessory-name/{hmsNr}") {
        val hmsnr = call.parameters["hmsNr"]!!
        logg.info("Request for name lookup for hmsnr=$hmsnr.")
        call.respond(suggestionService.hentTilbehørNavn(hmsnr))
    }

    get("/introspect") {
        call.respond(suggestionService.introspect()!!)
    }

    get("/inspection") { call.respondRedirect("/introspect") }
}
