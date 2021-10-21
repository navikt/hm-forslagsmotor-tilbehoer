package no.nav.hjelpemidler.suggestionengine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.azure.AzureClient
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.oebs.Oebs
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime

private val logg = KotlinLogging.logger {}

object SuggestionEngine {
    private val items = mutableMapOf<String, Item>()

    private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)
    private var azTokenTimeout: LocalDateTime? = null
    private var azToken: String? = null

    private val noDescription = "(beskrivelse utilgjengelig)"

    @Synchronized
    fun causeInit() {
        if (Configuration.application["APP_PROFILE"]!! != "local") {
            logg.info("Loading initial dataset for Suggestion Engine.")
            val initialDataset = getInitialDataset()
            learnFromSoknad(initialDataset)

            val totalProductsWithAccessorySuggestions = items.count()
            val totalAccessorySuggestions = items.map { i -> i.value.suggestions.count() }.reduce { i, j -> i + j }
            val totalAccessoriesWithoutADescription = items.map { i -> i.value.suggestions.filter { j -> j.value.title == noDescription }.count() }.reduce { i, j -> i + j }
            logg.info("Suggestion engine ínitial dataset loaded (totalProductsWithAccessorySuggestions=$totalProductsWithAccessorySuggestions, totalAccessorySuggestions=$totalAccessorySuggestions, totalAccessoriesWithoutADescription=$totalAccessoriesWithoutADescription)")

            AivenMetrics().totalProductsWithAccessorySuggestions(totalProductsWithAccessorySuggestions.toLong())
            AivenMetrics().totalAccessorySuggestions(totalAccessorySuggestions.toLong())
            AivenMetrics().totalAccessoriesWithoutADescription(totalAccessoriesWithoutADescription.toLong())
        }
    }

    @Synchronized
    fun learnFromSoknad(hjelpemidler: List<Hjelpemiddel>) {
        logg.info("Learning from Søknad.")
        for (hjelpemiddel in hjelpemidler) {
            if (!items.containsKey(hjelpemiddel.hmsNr)) {
                items[hjelpemiddel.hmsNr] = Item(mutableMapOf())
            }

            val suggestions = items[hjelpemiddel.hmsNr]!!.suggestions
            for (tilbehoer in hjelpemiddel.tilbehorListe) {
                if (!suggestions.contains(tilbehoer.hmsnr)) {
                    var description = noDescription
                    try {
                        description = Oebs.GetTitleForHmsNr(tilbehoer.hmsnr)
                    } catch (e: Exception) {
                        logg.warn("warn: failed to get title for hmsnr from hm-oebs-api-proxy")
                        e.printStackTrace()
                    }
                    // TODO: If we have the product in our dataset from hmdb, use that as a source of description instead.
                    suggestions[tilbehoer.hmsnr] = Suggestion(
                        tilbehoer.hmsnr,
                        description,
                        0,
                    )
                }

                // TODO: Consider if quantity is a good measure here, or if occurrences only counts as one no matter how many was requested.
                suggestions[tilbehoer.hmsnr]!!.occurancesInSoknader += 1
            }
        }
    }

    @Synchronized
    fun suggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        logg.info("Suggestions for hmsnr=$hmsNr.")
        return items[hmsNr]?.suggestions?.map { it.value }?.filter { it.occurancesInSoknader > 4 }?.sortedByDescending { it.occurancesInSoknader }?.take(20) ?: listOf()
    }

    // Mostly useful for testing (cleanup between tests)
    @Synchronized
    fun discardDataset() {
        logg.info("Discarding dataset.")
        items.clear()
    }

    private fun getInitialDataset(): List<Hjelpemiddel> {
        // Generate azure ad token for authorization header
        if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
            val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB"]!!)
            azToken = token.accessToken
            azTokenTimeout = LocalDateTime.now()
                .plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
        }
        val authToken = azToken!!

        // Make request
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://hm-soknadsbehandling-db/api/forslagsmotor/tilbehoer/datasett"))
            .timeout(Duration.ofMinutes(1))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $authToken")
            .GET()
            .build()

        val response: HttpResponse<String> = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            logg.info("error: unexpected status code: statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${response.body().take(40)}")
            return listOf()
        }

        return jacksonObjectMapper().readValue<Array<Hjelpemiddel>>(response.body()).asList()
    }
}

data class Hjelpemiddel(
    val hmsNr: String,
    val tilbehorListe: List<Tilbehoer>,
)

data class Tilbehoer(
    val hmsnr: String,
    val antall: Int,
    val navn: String,
)

data class Suggestion(
    val hmsNr: String,
    val title: String,

    var occurancesInSoknader: Int,
) {
    fun toFrontendFiltered(): SuggestionFrontendFiltered {
        return SuggestionFrontendFiltered(hmsNr, title)
    }
}

data class SuggestionFrontendFiltered(
    val hmsNr: String,
    val title: String,
)

private class Item(
    var suggestions: MutableMap<String, Suggestion>,
)
