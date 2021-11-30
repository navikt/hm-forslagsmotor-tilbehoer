package no.nav.hjelpemidler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.azure.AzureClient
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.suggestionengine2.SuggestionEngine
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

object InitialDataset {
    private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private var isInitialDatasetLoaded = false

    fun fetchInitialDatasetFor(se: SuggestionEngine) {
        thread(isDaemon = true) {
            // Generate azure ad token for authorization header
            val authToken = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB"]!!).accessToken

            // Make request
            val request: HttpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://hm-soknadsbehandling-db/api/forslagsmotor/tilbehoer/datasett"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $authToken")
                .GET()
                .build()

            val response: HttpResponse<String> =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                throw Exception(
                    "error: unexpected status code: statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${
                    response.body().take(40)
                    }"
                )
            }

            // Set initial dataset to suggestion engine
            se.learnFromSoknader(objectMapper.readValue<Array<no.nav.hjelpemidler.suggestionengine2.Soknad>>(response.body()).asList())

            // We have now loaded the dataset
            synchronized(this) {
                isInitialDatasetLoaded = true
            }
        }
    }

    @Synchronized
    fun isInitialDatasetLoaded(): Boolean {
        return isInitialDatasetLoaded
    }
}
