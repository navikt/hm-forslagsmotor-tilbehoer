package no.nav.hjelpemidler.suggestionengine

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.azure.AzureClient
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.suggestionengine2.SuggestionEngine
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val logg = KotlinLogging.logger {}

object SuggestionEngine {
    private val waitForReadyLock: ReentrantLock = ReentrantLock()
    private var datasetLoaded: Boolean = false

    private val items = mutableMapOf<String, Item>()
    private var knownSoknadIds = mutableListOf<UUID>()

    private var fakeLookupTable: Map<String, String>? = null

    private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)

    private val noDescription = "(beskrivelse utilgjengelig)"

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private val se2 = SuggestionEngine()

    fun isInitialDatasetLoaded(): Boolean {
        var dsLoaded = false
        if (waitForReadyLock.tryLock()) {
            dsLoaded = datasetLoaded
            waitForReadyLock.unlock()
        }
        return dsLoaded
    }

    private fun backgroundRunnder() {
        while (true) {
            Thread.sleep(10_000)
            backgroundRunnerSync()
        }
    }

    @Synchronized
    private fun backgroundRunnerSync() {
        var changesMade = false
        var remove404List: MutableList<Pair<String, String>> = mutableListOf()
        for (item in items) {
            for (suggestion in item.value.suggestions) {
                if (suggestion.value.title == noDescription) {
                    logg.info("Attempting to refetch title for suggestion with missing title (hmsNr=${suggestion.value.hmsNr})")
                    try {
                        val newDescription = Oebs.GetTitleForHmsNr(suggestion.value.hmsNr)
                        changesMade = true
                        items[item.key]!!.suggestions[suggestion.key] = Suggestion(suggestion.value.hmsNr, newDescription.first, suggestion.value.occurancesInSoknader)
                    } catch (e: Exception) {
                        // Ignoring non-existing products (statusCode=404), others will be added with
                        // title=noDescription and is thus not returned in suggestion results until the
                        // backgroundRunner retries and fetches the title.
                        if (e.toString().contains("statusCode=404")) {
                            logg.info("Ignoring suggestion with hmsNr=${suggestion.value.hmsNr} as OEBS returned 404 not found (product doesnt exist): $e")
                            changesMade = true
                            remove404List.add(Pair(item.key, suggestion.key))
                            continue
                        }

                        logg.error("Exception thrown during attempt to refetch OEBS title after previous failure (for hmsNr=${suggestion.value.hmsNr}): $e")
                        e.printStackTrace()
                    }
                }
            }
        }
        if (changesMade) {
            try {
                logg.info("Removing ignored suggestions if any (len=${remove404List.count()})")
                for (toRemove in remove404List) {
                    items[toRemove.first]!!.suggestions.remove(toRemove.second)
                    if (items[toRemove.first]!!.suggestions.isEmpty()) items.remove(toRemove.first)
                }

                logg.info("Calculating updated metrics for Suggestion Engine.")

                val totalProductsWithAccessorySuggestions = items.count()
                val totalAccessorySuggestions = items.map { i -> i.value.suggestions.count() }.fold(0) { i, j -> i + j }
                val totalAccessoriesWithoutADescription = items.map { i -> i.value.suggestions.filter { j -> j.value.title == noDescription }.count() }.fold(0) { i, j -> i + j }

                logg.info("\t- New metrics (totalProductsWithAccessorySuggestions=$totalProductsWithAccessorySuggestions, totalAccessorySuggestions=$totalAccessorySuggestions, totalAccessoriesWithoutADescription=$totalAccessoriesWithoutADescription)")

                AivenMetrics().totalProductsWithAccessorySuggestions(totalProductsWithAccessorySuggestions.toLong())
                AivenMetrics().totalAccessorySuggestions(totalAccessorySuggestions.toLong())
                AivenMetrics().totalAccessoriesWithoutADescription(totalAccessoriesWithoutADescription.toLong())
            } catch (e: Exception) {
                logg.error("Failed to report new metrics: $e")
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun causeInit() {
        if (Configuration.application["APP_PROFILE"]!! != "local") {
            try {
                logg.info("Downloading initial dataset for Suggestion Engine from hm-soknadsbehandling-db.")
                val (initialDataset, initialDatasetV2) = getInitialDataset()

                se2.learnFromSoknader(initialDatasetV2)

                logg.info("Storing list of known soknadIds (nrSoknads=${initialDataset.count()}):")
                for (intialDatasetSoknad in initialDataset) knownSoknadIds.add(intialDatasetSoknad.soknad.id)

                logg.info("Loading initial dataset for Suggestion Engine into memory (len=${initialDataset.count()}).")
                initialDataset.forEachIndexed { index, initialDatasetSoknad -> learnFromSoknad(initialDatasetSoknad.soknad.hjelpemidler.hjelpemiddelListe, true, index, initialDataset.count()) }

                logg.info("Calculating metrics on initial dataset for Suggestion Engine.")

                val totalProductsWithAccessorySuggestions = items.count()
                val totalAccessorySuggestions = items.map { i -> i.value.suggestions.count() }.fold(0) { i, j -> i + j }
                val totalAccessoriesWithoutADescription = items.map { i -> i.value.suggestions.filter { j -> j.value.title == noDescription }.count() }.fold(0) { i, j -> i + j }

                logg.info("Suggestion engine ínitial dataset loaded (totalProductsWithAccessorySuggestions=$totalProductsWithAccessorySuggestions, totalAccessorySuggestions=$totalAccessorySuggestions, totalAccessoriesWithoutADescription=$totalAccessoriesWithoutADescription)")

                AivenMetrics().totalProductsWithAccessorySuggestions(totalProductsWithAccessorySuggestions.toLong())
                AivenMetrics().totalAccessorySuggestions(totalAccessorySuggestions.toLong())
                AivenMetrics().totalAccessoriesWithoutADescription(totalAccessoriesWithoutADescription.toLong())

                // Notify isready that we are ready to proccess messages
                waitForReadyLock.lock()
                datasetLoaded = true
                waitForReadyLock.unlock()
            } catch (e: Exception) {
                // We use this rather than an exception to cause whole app to crash (cause restart loop until things are
                // good again), and not only the daemon thread
                logg.error("Fatal exception while downloading the intial dataset: $e")
                e.printStackTrace()
                exitProcess(-1)
            }

            thread(isDaemon = true) {
                logg.info("Starting background runner that retries fetching OEBS descriptions if it failed earlier")
                backgroundRunnder()
            }
        }
    }

    @Synchronized
    fun knownSoknadsId(soknadsId: UUID): Boolean {
        return knownSoknadIds.contains(soknadsId)
    }

    @Synchronized
    fun recordSoknadId(soknadsId: UUID) {
        knownSoknadIds.add(soknadsId)
    }

    @Synchronized
    fun learnFromSoknad(hjelpemidler: List<Hjelpemiddel>, initialDataset: Boolean = false, index: Int = 0, total: Int = 0) {
        if (initialDataset) {
            if (index % 1000 == 0) logg.info("(" + (index + 1).toString() + "/$total) Learning from initial dataset Søknad (only printing every 1000th søknad to limit output).")
        } else {
            logg.info("Learning from new incoming Søknad.")
        }
        for (hjelpemiddel in hjelpemidler) {
            if (!items.containsKey(hjelpemiddel.hmsNr) && hjelpemiddel.tilbehorListe.isNotEmpty()) {
                items[hjelpemiddel.hmsNr] = Item(mutableMapOf())
            }

            val suggestions = items[hjelpemiddel.hmsNr]!!.suggestions
            for (tilbehoer in hjelpemiddel.tilbehorListe) {
                if (tilbehoer.hmsnr == "000000") continue // Ignore this old hack to specify accessories by input-field
                if (!suggestions.contains(tilbehoer.hmsnr)) {
                    var description = noDescription
                    try {
                        description = if (fakeLookupTable == null) {
                            val oebsTitleAndType = Oebs.GetTitleForHmsNr(tilbehoer.hmsnr)
                            logg.info("DEBUG: Fetched title for ${tilbehoer.hmsnr} and oebs report it as having type: ${oebsTitleAndType.second}. Title: ${oebsTitleAndType.first}")
                            oebsTitleAndType.first
                        } else {
                            fakeLookupTable!![tilbehoer.hmsnr] ?: noDescription
                        }
                    } catch (e: Exception) {
                        // Ignoring non-existing products (statusCode=404), others will be added with
                        // title=noDescription and is thus not returned in suggestion results until the
                        // backgroundRunner retries and fetches the title.
                        if (e.toString().contains("statusCode=404")) {
                            logg.info("Ignoring suggestion with hmsNr=${tilbehoer.hmsnr} as OEBS returned 404 not found (product doesnt exist): $e")
                            continue
                        }
                        logg.warn("warn: failed to get title for hmsnr from hm-oebs-api-proxy")
                        e.printStackTrace()
                    }
                    suggestions[tilbehoer.hmsnr] = Suggestion(
                        tilbehoer.hmsnr,
                        description,
                        0,
                    )
                }

                suggestions[tilbehoer.hmsnr]!!.occurancesInSoknader += 1
            }

            if (suggestions.count() == 0) {
                items.remove(hjelpemiddel.hmsNr)
            }
        }
    }

    @Synchronized
    fun allSuggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return items[hmsNr]?.suggestions?.map { it.value }?.sortedByDescending { it.occurancesInSoknader } ?: listOf()
    }

    @Synchronized
    fun suggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        val some = allSuggestionsForHmsNr(hmsNr).filter { it.title != noDescription && it.occurancesInSoknader > 4 }.take(20)
        val some2 = se2.suggestionsForHmsNr(hmsNr)
        logg.info("DEBUG: HERE: Comparing suggestions for $hmsNr")
        if (some.count() != some2.count()) {
            logg.info("DEBUG: HERE: Suggestion lists in v1 and v2 doesnt match: $some != $some2")
        } else {
            var allMatching = true
            for (i in 0 until some.count()) {
                if (some[i].hmsNr != some2[i].hmsNr) {
                    allMatching = false
                    logg.info("DEBUG: HERE: Suggestion spot ${i + 1} does not match between v1 and v2: ${some[i].title} (${some[i].hmsNr})} != ${some2[i].title} (${some2[i].hmsNr})}")
                }
            }
            if (allMatching) {
                logg.info("DEBUG: HERE: All suggestions for $hmsNr matches!")
            }
        }
        return some
    }

    @Synchronized
    fun suggestionsForHmsNrWithNoDescription(hmsNr: String): List<Suggestion> {
        return allSuggestionsForHmsNr(hmsNr).filter { it.occurancesInSoknader > 4 }
    }

    // Mostly useful for testing (cleanup between tests)
    @Synchronized
    fun discardDataset() {
        logg.info("Discarding dataset.")
        items.clear()
        fakeLookupTable = null
    }

    // Mostly useful for testing (cleanup between tests)
    @Synchronized
    fun fakeNameLookupTable(lookupTable: Map<String, String>) {
        fakeLookupTable = lookupTable
    }

    private fun getInitialDataset(): Pair<List<Hjelpemidler>, List<no.nav.hjelpemidler.suggestionengine2.Soknad>> {
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

        val response: HttpResponse<String> = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw Exception("error: unexpected status code: statusCode=${response.statusCode()} headers=${response.headers()} body[:40]=${response.body().take(40)}")
        }

        return Pair<List<Hjelpemidler>, List<no.nav.hjelpemidler.suggestionengine2.Soknad>>(
            objectMapper.readValue<Array<Hjelpemidler>>(response.body()).asList(),
            objectMapper.readValue<Array<no.nav.hjelpemidler.suggestionengine2.Soknad>>(response.body()).asList()
        )
    }
}

data class Hjelpemidler(
    val soknad: Soknad,
    val created: LocalDateTime,
)

data class Soknad(
    val id: UUID,
    val hjelpemidler: SoknadInner
)

data class SoknadInner(
    val hjelpemiddelListe: List<Hjelpemiddel>,
)

data class Hjelpemiddel(
    val hmsNr: String,
    val tilbehorListe: List<Tilbehoer>,
)

data class Tilbehoer(
    val hmsnr: String,
    val antall: Int,
    val navn: String,
    val brukAvForslagsmotoren: BrukAvForslagsmotoren?,
)

data class BrukAvForslagsmotoren(
    val lagtTilFraForslagsmotoren: Boolean,
    val oppslagAvNavn: Boolean,
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
