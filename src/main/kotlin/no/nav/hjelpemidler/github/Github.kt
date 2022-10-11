package no.nav.hjelpemidler.github

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class BestillingsHjelpemiddel(
    val hmsnr: String,
    val navn: String,
    val tilbehor: List<String>?
)

object Github {
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun hentBestillingsordningSortiment(): List<BestillingsHjelpemiddel> {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://navikt.github.io/digihot-sortiment/test_bestillingsordning_sortiment.json"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response: HttpResponse<String> = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw Exception("error: unexpected status code from github (statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${response.body().take(40)})")
        }

        return objectMapper.readValue(response.body())
    }
}