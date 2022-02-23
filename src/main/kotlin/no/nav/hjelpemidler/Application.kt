package no.nav.hjelpemidler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.routing
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.db.SoknadStore
import no.nav.hjelpemidler.db.SoknadStorePostgres
import no.nav.hjelpemidler.db.dataSource
import no.nav.hjelpemidler.db.migrate
import no.nav.hjelpemidler.db.waitForDB
import no.nav.hjelpemidler.model.ProductFrontendFiltered
import no.nav.hjelpemidler.model.SuggestionsFrontendFiltered
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.rivers.NySøknadInnsendt
import no.nav.hjelpemidler.soknad.db.client.hmdb.HjelpemiddeldatabaseClient
import kotlin.system.measureTimeMillis
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
    val store = SoknadStorePostgres(dataSource())

    InitialDataset.fetchInitialDatasetFor(store)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.aivenConfig))
        .withKtorModule {
            installAuthentication()
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            routing {
                get("/isready-composed") {
                    if (!InitialDataset.isInitialDatasetLoaded()) {
                        call.respondText("NOT READY", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
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

fun Route.ktorRoutes(store: SoknadStore) {
    authenticate("tokenX", "aad") {
        get("/suggestions/{hmsNr}") {
            val hmsnr = call.parameters["hmsnr"]!!

            logg.info("Request for suggestions for hmsnr=$hmsnr.")
            val suggestions = store.suggestions(hmsnr)

            val hmsNrsSkipList = HjelpemiddeldatabaseClient
                .hentProdukterMedHmsnrs(suggestions.suggestions.map { it.hmsNr }.toSet())
                .filter { it.hmsnr != null && it.tilgjengeligForDigitalSoknad }
                .map { it.hmsnr!! }

            val results = SuggestionsFrontendFiltered(
                suggestions.dataStartDate,
                suggestions.suggestions.filter { !hmsNrsSkipList.contains(it.hmsNr) }
                    .map { it.toFrontendFiltered() },
            )

            call.respond(results)
        }

        get("/lookup-accessory-name/{hmsNr}") {
            val hmsnr = call.parameters["hmsNr"]!!

            logg.info("Request for name lookup for hmsnr=$hmsnr.")
            runCatching {
                // Søknaden er avhengig av denne gamle sjekken, da den egentlig sjekker om produktet eksisterer i hmdb
                // og hvis så om den er tilgjengelig for å legges til igjennom digital søknad som hovedprodukt.
                var accessory = true
                val hmdbResults = HjelpemiddeldatabaseClient.hentProdukterMedHmsnr(hmsnr)
                if (hmdbResults.any { it.tilgjengeligForDigitalSoknad }) {
                    logg.info("DEBUG: product looked up with /lookup-accessory-name was not really an accessory")
                    accessory = false
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
                        if (!accessory) {
                            "ikke et tilbehør"
                        } else {
                            null
                        }
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
            }
            logg.info("Request for introspection of suggestions (timeElapsed=$timeElapsed)")
            call.respond(result!!)
        }

        get("/inspection") { call.respondRedirect("/introspect") }
    }

    // FIXME: Remove before prod.
    get("/suggestions-v2/{hmsNr}") {
        val hmsNr = call.parameters["hmsNr"]!!

        logg.info("Request for suggestions v2 for hmsnr=$hmsNr.")
        val suggestions = store.suggestions(hmsNr)

        val results = SuggestionsFrontendFiltered(
            suggestions.dataStartDate,
            suggestions.suggestions.map { it.toFrontendFiltered() },
        )
        call.respond(results)
    }

    // FIXME: Remove before prod.
    get("/introspect-v2") {
        call.respond(store.introspect())
    }
}

data class LookupAccessoryName(
    val name: String?,
    val error: String?,
)
