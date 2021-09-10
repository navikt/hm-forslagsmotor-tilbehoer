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
import no.nav.hjelpemidler.suggestionengine.SuggestionFrontendFiltered

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
                    get("/nameLookup/{hmsNr}") {
                        val hmsNr = call.parameters["hmsNr"]!!
                        val result: NameLookup = try {
                            val name = Oebs.GetTitleForHmsNr(hmsNr)
                            NameLookup(name, null)
                        } catch (e: Exception) {
                            logg.info("warn: failed to find title for hmsNr=$hmsNr")
                            e.printStackTrace()
                            NameLookup(null, "produkt ikke funnet")
                        }
                        call.respond(result)
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

data class NameLookup (
    val name: String?,
    val error: String?,
)