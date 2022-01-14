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
import io.ktor.routing.get
import io.ktor.routing.routing
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.rivers.NySøknadInnsendt
import no.nav.hjelpemidler.soknad.db.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine
import no.nav.hjelpemidler.suggestionengine.SuggestionsFrontendFiltered

private val logg = KotlinLogging.logger {}

private val se = SuggestionEngine()

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

fun main() {
    InitialDataset.fetchInitialDatasetFor(se)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.aivenConfig))
        .withKtorModule {
            installAuthentication()
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            routing {
                get("/isready-composed") {
                    if (!InitialDataset.isInitialDatasetLoaded() || !se.isReady()) {
                        call.respondText("NOT READY", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    call.respondRedirect("/isready")
                }
                authenticate("tokenX", "aad") {
                    get("/suggestions/{hmsNr}") {
                        val hmsNr = call.parameters["hmsNr"]!!
                        logg.info("Request for suggestions for hmsnr=$hmsNr.")
                        val suggestions = se.suggestionsForHmsNr(hmsNr)
                        val hmsNrsSkipList = HjelpemiddeldatabaseClient
                            .hentProdukterMedHmsnrs(suggestions.suggestions.map { it.hmsNr }.toSet())
                            .filter { it.hmsnr != null && it.tilgjengeligForDigitalSoknad }
                            .map { it.hmsnr!! }
                        val results = SuggestionsFrontendFiltered(
                            suggestions.dataStartDate,
                            suggestions.suggestions.filter { !hmsNrsSkipList.contains(it.hmsNr) }
                                .map { it.toFrontendFiltered() }
                        )
                        call.respond(results)
                    }
                    get("/lookup-accessory-name/{hmsNr}") {
                        val hmsNr = call.parameters["hmsNr"]!!
                        logg.info("Request for name lookup for hmsnr=$hmsNr.")
                        try {
                            var accessory = true
                            val hmdbResults = HjelpemiddeldatabaseClient.hentProdukterMedHmsnr(hmsNr)
                            if (hmdbResults.any { it.tilgjengeligForDigitalSoknad }) {
                                logg.info("DEBUG: product looked up with /lookup-accessory-name was not really an accessory")
                                accessory = false
                            }
                            val oebsTitleAndType = Oebs.getTitleForHmsNr(hmsNr)
                            logg.info("DEBUG: Fetched title for $hmsNr and oebs report it as having type: ${oebsTitleAndType.second}. Title: ${oebsTitleAndType.first}")
                            if (oebsTitleAndType.second != "Del") {
                                logg.info("DEBUG: $hmsNr is not a \"DEl\" according to OEBS (type=${oebsTitleAndType.second}; title=${oebsTitleAndType.first})")
                                // accessory = false
                            }
                            call.respond(
                                LookupAccessoryName(
                                    oebsTitleAndType.first,
                                    if (!accessory) {
                                        "ikke et tilbehør"
                                    } else {
                                        null
                                    }
                                )
                            )
                        } catch (e: Exception) {
                            logg.info("warn: failed to find title for hmsNr=$hmsNr")
                            e.printStackTrace()
                            call.respond(LookupAccessoryName(null, "produkt ikke funnet"))
                        }
                    }
                    get("/inspection") {
                        call.respond(se.inspectionOfSuggestions())
                    }
                }
            }
        }
        .build().apply {
            NySøknadInnsendt(this, se)
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
