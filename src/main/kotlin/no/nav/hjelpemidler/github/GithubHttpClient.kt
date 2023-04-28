package no.nav.hjelpemidler.github

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.routing.HttpMethodRouteSelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface GithubClient {
    fun hentBestillingsordningSortiment(): List<BestillingsHjelpemiddel>
    fun hentTilbehørslister(): Delelister
    fun hentReservedelslister(): Delelister
}

// TODO: Bruk ktor-client og suspending functions

class GithubHttpClient(
    private val digihotSortimentUrl: String = "https://navikt.github.io/digihot-sortiment",
    private val tilbehørslisterUrl: String = "https://navikt.github.io/hm-utils/tilbehor.json",
    private val reservedelslisterUrl: String = "https://navikt.github.io/hm-utils/reservedeler.json"
) : GithubClient {

    override fun hentBestillingsordningSortiment(): List<BestillingsHjelpemiddel> {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$digihotSortimentUrl/bestillingsordning_sortiment.json"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response: HttpResponse<String> =
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw Exception(
                "error: unexpected status code from github (statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${
                    response.body().take(40)
                })"
            )
        }

        return objectMapper.readValue(response.body())
    }

    override fun hentTilbehørslister(): Delelister {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(tilbehørslisterUrl))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response: HttpResponse<String> =
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw Exception(
                "error: unexpected status code from github (statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${
                    response.body().take(40)
                })"
            )
        }

        return objectMapper.readValue(response.body())
    }

    override fun hentReservedelslister(): Delelister {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(reservedelslisterUrl))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response: HttpResponse<String> =
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw Exception(
                "error: unexpected status code from github (statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${
                    response.body().take(40)
                })"
            )
        }

        return objectMapper.readValue(response.body())
    }
}

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)