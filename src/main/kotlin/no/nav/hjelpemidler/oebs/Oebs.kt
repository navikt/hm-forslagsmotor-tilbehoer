package no.nav.hjelpemidler.oebs

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
import java.time.LocalDateTime

private val logg = KotlinLogging.logger {}

object Oebs {
    private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)
    private var azTokenTimeout: LocalDateTime? = null
    private var azToken: String? = null

    fun GetTitleForHmsNr(hmsNr: String): String {
        // Generate azure ad token for authorization header
        if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
            val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE_OEBSAPIPROXY"]!!)
            azToken = token.accessToken
            azTokenTimeout = LocalDateTime.now()
                .plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
        }
        val authToken = azToken!!

        // Make request
        val baseurl = Configuration.application["OEBS_API_PROXY_URL"]!!
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseurl/getTitleForHmsNr/$hmsNr"))
            .timeout(Duration.ofMinutes(1))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $authToken")
            .GET()
            .build()

        val response: HttpResponse<String> = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            logg.info("error: unexpected status code: statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${response.body().take(40)}")
            throw Exception("error: unexpected status code from oebs api proxy: statusCode=${response.statusCode()}")
        }

        return jacksonObjectMapper().readValue<ResponseGetTitleForHmsNr>(response.body()).title
    }
}

data class ResponseGetTitleForHmsNr(
    val hmsNr: Int,
    val title: String,
)
