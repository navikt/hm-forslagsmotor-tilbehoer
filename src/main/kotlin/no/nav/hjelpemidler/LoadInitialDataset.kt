package no.nav.hjelpemidler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.azure.AzureClient
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.db.SoknadStore
import no.nav.hjelpemidler.db.SoknadStorePostgres
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val logg = KotlinLogging.logger {}

object InitialDataset {
    private val azClient = AzureClient(
        Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!,
        Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!,
        Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!
    )

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private var isInitialDatasetLoaded = false

    internal fun fetchInitialDatasetFor(se: SuggestionEngine, store: SoknadStore) {
        thread(isDaemon = true) {
            logg.info("Waiting on network before downloading initial dataset")
            Thread.sleep(5000)

            // Generate azure ad token for authorization header
            val authToken = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB"]!!).accessToken

            // Make request
            var response: HttpResponse<String>? = null
            val attempts = 2
            for (attempt in 0..attempts) {
                logg.info("Attempting to download dataset")
                val request: HttpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://hm-soknadsbehandling-db/api/forslagsmotor/tilbehoer/datasett"))
                    .timeout(Duration.ofMinutes(1))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $authToken")
                    .GET()
                    .build()

                try {
                    response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
                    break
                } catch (e: Exception) {
                    logg.info("Download of initial dataset failed attempt=$attempt, attempting $attempts times...")
                    if (attempt == attempts) {
                        logg.error("No more attempts, the last one failed with: $e. Exiting process with error code -123")
                        exitProcess(-123)
                    }
                }
            }

            // Has to be set from this point!
            response!!

            if (response.statusCode() != 200) {
                logg.error(
                    "error: unexpected status code: statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${
                    response.body().take(40)
                    }(...)"
                )
                exitProcess(-123)
            }

            val dataset = objectMapper.readValue<Array<no.nav.hjelpemidler.suggestionengine.Soknad>>(response.body()).asList()
            if (dataset.isEmpty()) {
                logg.error("Empty dataset received from hm-soknadsbehandling-db, unable to continue")
                exitProcess(-123)
            }

            logg.info("Download initial dataset finished with ${dataset.count()} applications to learn from")

            // Set initial dataset to suggestion engine
            se.learnFromSoknader(
                dataset
            )

            // Add to database
            store.processApplications(dataset)

            // We have now loaded the dataset
            synchronized(this) {
                isInitialDatasetLoaded = true
            }

            logg.info("Initial dataset loaded set to true!")
        }
    }

    @Synchronized
    fun isInitialDatasetLoaded(): Boolean {
        return isInitialDatasetLoaded
    }
}
