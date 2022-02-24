package no.nav.hjelpemidler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.db.dataSource
import no.nav.hjelpemidler.db.migrate
import no.nav.hjelpemidler.db.waitForDB
import no.nav.hjelpemidler.rivers.NySøknadInnsendt
import no.nav.hjelpemidler.suggestionengine.SuggestionEnginePostgres
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

private val logg = KotlinLogging.logger {}

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

@ExperimentalTime
fun main() {
    if (!waitForDB(10.minutes)) {
        throw Exception("database never became available withing the deadline")
    }

    // Make sure our database migrations are up to date
    migrate()

    // Set up our database connection
    val store = SuggestionEnginePostgres(dataSource())

    // InitialDataset.fetchInitialDatasetFor(store)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.aivenConfig))
        .withKtorModule {
            installAuthentication()
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            routing {
                get("/isready-composed") {
                    // TODO: Check database connection
                    call.respondRedirect("/isready")
                }
                ktorRoutes(store)
            }
        }
        .build().apply {
            NySøknadInnsendt(this, store)
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
