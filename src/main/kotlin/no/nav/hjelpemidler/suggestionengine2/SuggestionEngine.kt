package no.nav.hjelpemidler.suggestionengine2

import io.ktor.utils.io.core.Closeable
import java.time.LocalDate

class SuggestionEngine(
    testingOebsDatabase: Map<String, String>? = null,
    testingHmdbDatabase: Map<String, LocalDate>? = null,
    testingSoknadDatabase: List<Soknad>? = null
) : Closeable {
    private val oebsDatabase = OebsDatabase(testingOebsDatabase)
    private val hmdbDatabase = HmdbDatabase(testingHmdbDatabase)
    private val soknadDatabase = SoknadDatabase(testingSoknadDatabase)

    override fun close() {
        oebsDatabase.close()
        hmdbDatabase.close()
        soknadDatabase.close()
    }

    fun allSuggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return generateSuggestionsFor(hmsNr)
    }

    fun suggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return allSuggestionsForHmsNr(hmsNr).filter { it.occurancesInSoknader > 4 }.take(20)
    }

    fun learnFromSoknad(soknad: Soknad) {
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
                    title = oebsDatabase.getTitleFor(hmsNr) ?: "",
                )
            }
            suggestions[hmsNr]!!.occurancesInSoknader++
        }

        return suggestions.toList().map { it.second }.filter { it.isReady() }
            .sortedByDescending { it.occurancesInSoknader }
    }
}
