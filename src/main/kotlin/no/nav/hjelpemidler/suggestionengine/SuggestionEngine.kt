package no.nav.hjelpemidler.suggestionengine

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import no.nav.hjelpemidler.metrics.AivenMetrics
import java.time.LocalDate
import java.util.UUID
import kotlin.system.measureTimeMillis

private val logg = KotlinLogging.logger {}

private val MIN_NUMBER_OF_OCCURANCES = 4

class SuggestionEngine(
    testingSoknadDatabase: List<Soknad>? = null,
    testingOebsDatabase: Map<String, String>? = null,
    testingHmdbDatabase: Map<String, LocalDate>? = null,
    val testingMode: Boolean = testingSoknadDatabase != null || testingOebsDatabase != null || testingHmdbDatabase != null,
) : Closeable {

    private val soknadDatabase = SoknadDatabase(testingSoknadDatabase)
    private val hmdbDatabase = HmdbDatabase(testingHmdbDatabase)

    private var hasHadInitialOebsDatabaseBackgroundRun: Boolean = false
    private val oebsDatabase = OebsDatabase(testingOebsDatabase) {
        // After a background run with changes, do:

        // Regenerate stats
        generateStats()

        // Set our boolean used during boot to wait for the initial background run to complete before setting isReady=true
        synchronized(hasHadInitialOebsDatabaseBackgroundRun) {
            hasHadInitialOebsDatabaseBackgroundRun = true
        }
    }

    override fun close() {
        soknadDatabase.close()
        oebsDatabase.close()
        hmdbDatabase.close()
    }

    fun isReady(): Boolean {
        synchronized(hasHadInitialOebsDatabaseBackgroundRun) {
            return hasHadInitialOebsDatabaseBackgroundRun
        }
    }

    fun allSuggestionsForHmsNr(hmsNr: String): Suggestions {
        return generateSuggestionsFor(hmsNr)
    }

    fun suggestionsForHmsNr(hmsNr: String): Suggestions {
        var suggestions = allSuggestionsForHmsNr(hmsNr)
        suggestions.suggestions =
            suggestions.suggestions.filter { it.isReady() && it.occurancesInSoknader > MIN_NUMBER_OF_OCCURANCES }.take(20)
        return suggestions
    }

    fun learnFromSoknad(soknad: Soknad) {
        learnFromSoknader(listOf(soknad))
    }

    fun learnFromSoknader(soknader: List<Soknad>) {
        for (soknad in soknader) {
            try {
                soknadDatabase.add(soknad) // Throws if already known

                soknad.soknad.hjelpemidler.hjelpemiddelListe.forEach {
                    if (oebsDatabase.getTitleFor(it.hmsNr) == null) oebsDatabase.setTitleFor(
                        it.hmsNr,
                        null
                    ) // Oebs's background runner takes things from here

                    it.tilbehorListe.forEach {
                        if (oebsDatabase.getTitleFor(it.hmsnr) == null) oebsDatabase.setTitleFor(
                            it.hmsnr,
                            null
                        ) // Oebs's background runner takes things from here
                    }

                    if (hmdbDatabase.getFrameworkAgreementStartFor(it.hmsNr) == null) hmdbDatabase.setFrameworkAgreementStartFor(
                        it.hmsNr,
                        null
                    ) // Hmdb's background runner takes things from here
                }
            } catch (e: Exception) {
                logg.info("Exception thrown while adding soknads: $e")
                e.printStackTrace()
            }
        }

        // Recalculate metrics
        generateStats()
    }

    fun knowsOfSoknadID(soknadID: UUID): Boolean {
        return soknadDatabase.has(soknadID)
    }

    fun inspectionOfSuggestions(): List<ProductFrontendFiltered> {
        return measureAndLogElapsedTime("inspectionOfSuggestions()") {
            val hmsNrs = soknadDatabase.getAllKnownProductHmsnrs()
            hmsNrs.map {
                val suggestions = measureAndLogElapsedTime("inspectionOfSuggestions()-generateSuggestionsFor") {
                    generateSuggestionsFor(it)
                }
                val s = suggestions
                    .suggestions.filter { it.occurancesInSoknader > MIN_NUMBER_OF_OCCURANCES && it.isReady() }
                ProductFrontendFiltered(it, oebsDatabase.getTitleFor(it) ?: "", s, suggestions.dataStartDate)
            }
        }.filter { it.suggestions.isNotEmpty() }
    }

    private fun generateSuggestionsFor(hmsNr: String): Suggestions {
        // Identify current framework agreement start/end date, use that to form suggestions
        val suggestionsFrom = hmdbDatabase.getFrameworkAgreementStartFor(hmsNr) ?: LocalDate.of(0, 1, 1)
        val suggestionsHasFromDate = suggestionsFrom.year != 0

        // Get a list of all accessories applied for with this product
        val accessories = soknadDatabase.getAccessoriesByProductHmsnr(hmsNr, suggestionsFrom)

        // Aggregate suggestions and count
        val suggestions: MutableMap<String, Suggestion> = mutableMapOf()
        for (accessory in accessories) {
            // If the title has been deleted automatically it means OEBS didnt know about it (404 not found),
            // and we wont suggest it here:
            if (!oebsDatabase.hasTitleReference(accessory.hmsnr)) continue

            if (!suggestions.containsKey(accessory.hmsnr)) {
                suggestions[accessory.hmsnr] = Suggestion(
                    hmsNr = accessory.hmsnr,
                    title = oebsDatabase.getTitleFor(accessory.hmsnr),
                )
            }
            suggestions[accessory.hmsnr]!!.occurancesInSoknader++
        }

        return Suggestions(
            if (suggestionsHasFromDate) {
                suggestionsFrom
            } else {
                null
            },
            suggestions.toList().map { it.second }.sortedByDescending { it.occurancesInSoknader }
        )
    }

    private fun generateStats() {
        // Fetch the list of all known product hmsNrs
        val hmsNrs = soknadDatabase.getAllKnownProductHmsnrs()

        // Transform list of unique hmsNrs into a map from hmsNr to list of suggestions (excluding any hmsNr that has no suggestions)
        val suggestions = hmsNrs.map { Pair(it, generateSuggestionsFor(it).suggestions) }
            .groupBy { it.first }
            .mapValues {
                it.value.map { it.second }.fold(mutableListOf<Suggestion>()) { a, b ->
                    a.addAll(b)
                    a
                }
            }
            .mapValues { it.value.toList() }
            .filter { it.value.isNotEmpty() }

        // Collect statistics on the resulting data
        val totalProductsWithAccessorySuggestions = suggestions.keys.count()
        val totalAccessorySuggestions = suggestions.map { it.value.count() }.fold(0) { a, b -> a + b }
        val totalAccessoriesWithoutADescription =
            suggestions.map { it.value.count { !it.isReady() } }.fold(0) { a, b -> a + b }

        // Report what we found to influxdb / grafana
        logg.info("Suggestion engine stats calculated (totalProductsWithAccessorySuggestions=$totalProductsWithAccessorySuggestions, totalAccessorySuggestions=$totalAccessorySuggestions, totalAccessoriesWithoutADescription=$totalAccessoriesWithoutADescription)")

        // Log info about what suggestions we have
        var allSuggestions = "All current suggestions (as seen by clients):\n\n"
        suggestions.mapValues {
            it.value.filter { it.occurancesInSoknader > MIN_NUMBER_OF_OCCURANCES }
                .sortedByDescending { it.occurancesInSoknader }
                .map { Pair(it.hmsNr, it.occurancesInSoknader) }
        }.filter { it.value.isNotEmpty() }
            .forEach {
                allSuggestions += "\tSuggestions for ${it.key}:\n"
                it.value.forEach {
                    allSuggestions += "\t\t- Suggestion: ${it.first}: ${it.second} occurrence(s)\n"
                }
            }
        logg.info(allSuggestions)

        if (!testingMode) {
            AivenMetrics().totalProductsWithAccessorySuggestions(totalProductsWithAccessorySuggestions.toLong())
            AivenMetrics().totalAccessorySuggestions(totalAccessorySuggestions.toLong())
            AivenMetrics().totalAccessoriesWithoutADescription(totalAccessoriesWithoutADescription.toLong())
        }
    }
}

fun <T> measureAndLogElapsedTime(name: String, block: () -> T): T {
    var result: T?
    val elapsedTime = measureTimeMillis {
        result = block()
    }
    logg.info("measureAndLogElapsedTime: Elapsed time \"$name\": ${elapsedTime}ms")
    return result!!
}
