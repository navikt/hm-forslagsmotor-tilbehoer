package no.nav.hjelpemidler

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.hjelpemidler.suggestions.SuggestionService

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
        val response = suggestionService.hentBestillingstilbehør(hmsnr)
        logg.info { "Returnerer response <$response> for bestilling tilbehøroppslag på hmsnr <$hmsnr>" }
        call.respond(response)
    }

    // @Deprecated: Bruk variant med både hmsnr for hovedprodukt og del
    get("/lookup-accessory-name/{hmsNr}") {
        val hmsnr = call.parameters["hmsNr"]!!
        logg.info("Request for name lookup for hmsnr=$hmsnr.")
        call.respond(suggestionService.hentTilbehør(hmsnr, null))
    }

    get("/lookup-accessory-name/{hmsnrHovedprodukt}/{hmsnrTilbehor}") {
        val hmsnrHovedprodukt = call.parameters["hmsnrHovedprodukt"]!!
        val hmsnrTilbehør = call.parameters["hmsnrTilbehor"]!!
        logg.info("Request for name lookup for hmsnrHovedprodukt=$hmsnrHovedprodukt and hmsnrTilbehør=$hmsnrTilbehør.")
        call.respond(suggestionService.hentTilbehør(hmsnrTilbehør, hmsnrHovedprodukt))
    }

    get("/introspect") {
        call.respond(suggestionService.introspect()!!)
    }

    get("/inspection") { call.respondRedirect("/introspect") }
}
