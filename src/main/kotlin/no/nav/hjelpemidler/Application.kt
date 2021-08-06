package no.nav.hjelpemidler

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.rivers.NySøknadInnsendt
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine

private val logg = KotlinLogging.logger {}

fun main() {
    SuggestionEngine.causeInit()

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.aivenConfig))
        .withKtorModule {
            routing {
                get("/suggestion/{hmsNr}") {
                    // TODO: Authentication: tokenX, m2m or on-behalf-of?
                    val hmsNr = call.parameters["hmsNr"]!!
                    call.respond(SuggestionEngine.suggestionsForHmsNr(hmsNr))
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
