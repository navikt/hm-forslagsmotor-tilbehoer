package no.nav.hjelpemidler

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.rivers.NySøknadInnsendt
import no.nav.hjelpemidler.suggestionengine.Suggestion
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine
import no.nav.hjelpemidler.suggestionengine.SuggestionFrontendFiltered
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

fun main() {
    thread(isDaemon = true) {
        logg.info("Causing init of Suggestion Engine in separate thread")
        SuggestionEngine.causeInit()
    }

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.aivenConfig))
        .withKtorModule {
            installAuthentication()
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter())
            }
            routing {
                get("/isready-composed") {
                    if (!SuggestionEngine.isInitialDatasetLoaded()) {
                        call.respondText("NOT READY", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    call.respondRedirect("/isready")
                }
                authenticate("tokenX") {
                    get("/suggestions/{hmsNr}") {
                        val hmsNr = call.parameters["hmsNr"]!!
                        logg.info("Request for suggestions for hmsnr=$hmsNr.")
                        val results: MutableList<SuggestionFrontendFiltered> = mutableListOf()
                        for (suggestion in SuggestionEngine.suggestionsForHmsNr(hmsNr)) {
                            results.add(
                                Suggestion(
                                    hmsNr = suggestion.hmsNr,
                                    title = suggestion.title,
                                    occurancesInSoknader = suggestion.occurancesInSoknader,
                                ).toFrontendFiltered()
                            )
                        }
                        call.respond(results)
                    }
                    get("/lookup-accessory-name/{hmsNr}") {
                        val hmsNr = call.parameters["hmsNr"]!!
                        logg.info("Request for name lookup for hmsnr=$hmsNr.")
                        try {
                            call.respond(LookupAccessoryName(Oebs.GetTitleForHmsNr(hmsNr), null))
                        } catch (e: Exception) {
                            logg.info("warn: failed to find title for hmsNr=$hmsNr")
                            e.printStackTrace()
                            call.respond(LookupAccessoryName(null, "produkt ikke funnet"))
                        }
                    }
                }
            }
        }
        .build().apply {
            NySøknadInnsendt(this)
        }.apply {
            register(
                object : RapidsConnection.StatusListener {
                    override fun onStartup(rapidsConnection: RapidsConnection) {
                        logg.debug("On rapid startup")
                    }

                    override fun onReady(rapidsConnection: RapidsConnection) {
                        logg.debug("On rapid ready")
                    }

                    override fun onNotReady(rapidsConnection: RapidsConnection) {
                        logg.debug("On rapid not ready")
                    }

                    override fun onShutdown(rapidsConnection: RapidsConnection) {
                        logg.debug("On rapid shutdown")
                    }
                }
            )
        }.start()

    logg.debug("Debug: After rapid start, end of main func")
}

data class LookupAccessoryName(
    val name: String?,
    val error: String?,
)
