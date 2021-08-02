package no.nav.hjelpemidler.suggestionengine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.hjelpemidler.azure.AzureClient
import no.nav.hjelpemidler.configuration.Configuration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime

object SuggestionEngine {
    private val items = mutableMapOf<String, Item>()

    private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)
    private var azTokenTimeout: LocalDateTime? = null
    private var azToken: String? = null

    init {
        if (Configuration.application["APP_PROFILE"]!! != "local") {
            println("Loading initial dataset for Suggestion Engine.")
            val initialDataset = getInitialDataset()
            learnFromSoknad(initialDataset)
            println("Suggestion engine ínitial dataset loaded (count=${items.size})")
        }
    }

    @Synchronized
    fun causeInit() {
    }

    @Synchronized
    fun learnFromSoknad(hjelpemidler: List<Hjelpemiddel>) {
        for (hjelpemiddel in hjelpemidler) {
            if (!items.containsKey(hjelpemiddel.hmsNr)) {
                items[hjelpemiddel.hmsNr] = Item(mutableMapOf())
            }

            val suggestions = items[hjelpemiddel.hmsNr]!!.suggestions
            for (tilbehoer in hjelpemiddel.tilbehorListe) {
                if (!suggestions.contains(tilbehoer.hmsnr)) {
                    suggestions[tilbehoer.hmsnr] = Suggestion(
                        tilbehoer.hmsnr,
                        tilbehoer.navn, // FIXME: Must be fetched from OEBS or other quality source
                        0,
                    )
                }

                // TODO: Consider if quantity is a good measure here, or if occurances only counts as one no matter how many was requested.
                suggestions[tilbehoer.hmsnr]!!.occurancesInSoknader += 1
            }
        }
    }

    @Synchronized
    fun suggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return items[hmsNr]?.suggestions?.map { it.value }?.sortedByDescending { it.occurancesInSoknader }?.take(40) ?: listOf()
    }

    // Mostly useful for testing (cleanup between tests)
    @Synchronized
    fun discardDataset() {
        items.clear()
    }

    private fun getInitialDataset(): List<Hjelpemiddel> {
        if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
            val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE"]!!)
            azToken = token.accessToken
            azTokenTimeout = LocalDateTime.now()
                .plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
        }
        val authToken = azToken!!

        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://hm-soknadsbehandling-db/api/forslagsmotor/tilbehoer/datasett"))
            .timeout(Duration.ofMinutes(1))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $authToken")
            .GET()
            .build()

        val rawJson: HttpResponse<String> = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        return jacksonObjectMapper().readValue<Array<Hjelpemiddel>>(rawJson.body()).asList()
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
)

private class Item(
    var suggestions: MutableMap<String, Suggestion>,
)
