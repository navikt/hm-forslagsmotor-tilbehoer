package no.nav.hjelpemidler.oebs

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.azure.AzureClient
import no.nav.hjelpemidler.configuration.Configuration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logg = KotlinLogging.logger {}

object Oebs {
    private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun GetTitleForHmsNr(hmsNr: String): String {
        // Generate azure ad token for authorization header
        val authToken = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE_OEBSAPIPROXY"]!!).accessToken

        // Make request
        val baseurl = Configuration.application["OEBS_API_PROXY_URL"]!!
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://$baseurl/get-title-for-hmsnr/$hmsNr"))
            .timeout(Duration.ofMinutes(1))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $authToken")
            .GET()
            .build()

        val response: HttpResponse<String> = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw Exception("error: unexpected status code from oebs api proxy (statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${response.body().take(40)})")
        }

        return objectMapper.readValue<ResponseGetTitleForHmsNr>(response.body()).title
    }
}

data class ResponseGetTitleForHmsNr(
    val hmsNr: String,
    val title: String,
)
