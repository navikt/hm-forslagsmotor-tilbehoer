package no.nav.hjelpemidler

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.response.respond
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

private val logg = KotlinLogging.logger {}

fun main() {
    SuggestionEngine.causeInit()

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.aivenConfig))
        .withKtorModule {
            installAuthentication()
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter())
            }
            routing {
                authenticate("tokenX") {
                    get("/suggestions/{hmsNr}") {
                        val hmsNr = call.parameters["hmsNr"]!!
                        val results: MutableList<Suggestion> = mutableListOf()
                        for (suggestion in SuggestionEngine.suggestionsForHmsNr(hmsNr)) {
                            var altTitle = ""
                            try {
                                altTitle = Oebs.GetTitleForHmsNr(suggestion.hmsNr)
                            } catch (e: Exception) {
                                logg.warn("Unable to fetch oebs alternative title for accessory suggestion (hmsNr=${suggestion.hmsNr}): $e")
                                e.printStackTrace()
                            }
                            results.add(
                                Suggestion(
                                    hmsNr = suggestion.hmsNr,
                                    title = if (altTitle.isNotEmpty()) altTitle else suggestion.title,
                                    occurancesInSoknader = suggestion.occurancesInSoknader,
                                )
                            )
                        }
                        call.respond(results)
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
