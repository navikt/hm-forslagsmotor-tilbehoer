package no.nav.hjelpemidler.suggestionengine2

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import java.time.LocalDate

private val logg = KotlinLogging.logger {}

class SuggestionEngine(
    testingSoknadDatabase: List<Soknad>? = null,
    testingOebsDatabase: Map<String, String>? = null,
    testingHmdbDatabase: Map<String, LocalDate>? = null,
) : Closeable {

    private val soknadDatabase = SoknadDatabase(testingSoknadDatabase)
    private val oebsDatabase = OebsDatabase(testingOebsDatabase)
    private val hmdbDatabase = HmdbDatabase(testingHmdbDatabase)

    override fun close() {
        soknadDatabase.close()
        oebsDatabase.close()
        hmdbDatabase.close()
    }

    fun allSuggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return generateSuggestionsFor(hmsNr)
    }

    fun suggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return allSuggestionsForHmsNr(hmsNr).filter { it.isReady() && it.occurancesInSoknader > 4 }.take(20)
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

                    if (hmdbDatabase.getFrameworkAgreementStartFor(it.hmsNr) == null) hmdbDatabase.setFrameworkAgreementStartFor(
                        it.hmsNr,
                        null
                    ) // Hmdb's background runner takes things from here
                }
            } catch (e: Exception) {
                logg.info("DEBUG: HERE: Exception thrown while adding soknads: $e")
                e.printStackTrace()
            }
        }

        // Recalculate metrics
        generateStats()
    }

    private fun generateSuggestionsFor(hmsNr: String): List<Suggestion> {
        // Identify current framework agreement start/end date, use that to form suggestions
        var suggestionsFrom = LocalDate.of(0, 1, 1)

        val frameworkAgreementStartDate = hmdbDatabase.getFrameworkAgreementStartFor(hmsNr)
        if (frameworkAgreementStartDate != null) {
            suggestionsFrom = frameworkAgreementStartDate
        }

        // Get a list of all accessories applied for with this product
        val accessories = soknadDatabase.getAccessoriesByProductHmsnr(hmsNr, suggestionsFrom)

        // Aggregate suggestions and count
        val suggestions: MutableMap<String, Suggestion> = mutableMapOf()
        for (accessory in accessories) {
            val hmsNr = accessory.hmsnr
            if (!suggestions.containsKey(hmsNr)) {
                suggestions[hmsNr] = Suggestion(
                    hmsNr = hmsNr,
                    title = oebsDatabase.getTitleFor(hmsNr),
                )
            }
            suggestions[hmsNr]!!.occurancesInSoknader++
        }

        return suggestions.toList().map { it.second }
            .sortedByDescending { it.occurancesInSoknader }
    }

    private fun generateStats() {
        // Fetch the list of all known product hmsNrs
        val hmsNrs = soknadDatabase.getAllKnownProductHmsnrs()

        // Transform list of unique hmsNrs into a map from hmsNr to list of suggestions (excluding any hmsNr that has no suggestions)
        val suggestions = hmsNrs.map { Pair<String, List<Suggestion>>(it, generateSuggestionsFor(it)) }
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

        // TODO: Report what we found to influxdb / grafana
        logg.info("Suggestion engine V2 (!!) stats calculated (totalProductsWithAccessorySuggestions=$totalProductsWithAccessorySuggestions, totalAccessorySuggestions=$totalAccessorySuggestions, totalAccessoriesWithoutADescription=$totalAccessoriesWithoutADescription)")
    }
}
