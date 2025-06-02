package no.nav.hjelpemidler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.db.dataSourceFrom
import no.nav.hjelpemidler.db.migrate
import no.nav.hjelpemidler.db.waitForDB
import no.nav.hjelpemidler.github.CachedGithubClient
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.rivers.NySøknadInnsendt
import no.nav.hjelpemidler.suggestions.SuggestionEnginePostgres
import no.nav.hjelpemidler.suggestions.SuggestionService
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

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

    logg.teamInfo { "LOGG_TEST denne burde dukke opp i Team Logs." }
    logg.info { "LOGG_TEST denne burde dukke opp i vanlig log." }

    // Make sure our database migrations are up to date
    migrate(Configuration)

    val aivenMetrics = AivenMetrics()
    val hjelpemiddeldatabaseClient = HjelpemiddeldatabaseClient()
    val githubClient = CachedGithubClient()
    val oebs = Oebs()

    // Set up our database connection
    val store = SuggestionEnginePostgres(dataSourceFrom(Configuration), aivenMetrics, hjelpemiddeldatabaseClient, oebs)

    val suggestionService = SuggestionService(store, aivenMetrics, hjelpemiddeldatabaseClient, githubClient, oebs)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.aivenConfig))
        .withKtorModule {
            installAuthentication()
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                }
            }
            routing {
                get("/isready-composed") {
                    // TODO: Check database connection
                    call.respondRedirect("/isready")
                }
                ktorRoutes(suggestionService)
            }
        }
        .build().apply {
            aivenMetrics.initMetabaseProducer(this)
            NySøknadInnsendt(this, store, aivenMetrics, githubClient)
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
