package no.nav.hjelpemidler.suggestionengine2

import no.nav.hjelpemidler.suggestionengine.SoknadDatabase
import java.time.LocalDateTime

object SuggestionEngine {

    fun allSuggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return generateSuggestionsFor(hmsNr)
    }

    fun suggestionsForHmsNr(hmsNr: String): List<Suggestion> {
        return allSuggestionsForHmsNr(hmsNr).filter { it.occurancesInSoknader > 4 }.take(20)
    }

    private fun generateSuggestionsFor(hmsNr: String): List<Suggestion> {
        // TODO: Identify current framework agreement start/end date, use that to form suggestions
        val suggestionsFrom = LocalDateTime.of(0, 0, 0, 0, 0)

        // Get a list of all accessories applied for with this product
        val accessories = SoknadDatabase.getAccessoriesByProductHmsnr(hmsNr, suggestionsFrom)

        // Aggregate suggestions and count
        val suggestions: MutableMap<String, Suggestion> = mutableMapOf()
        for (accessory in accessories) {
            val hmsNr = accessory.hmsnr
            if (!suggestions.containsKey(hmsNr)) {
                suggestions[hmsNr] = Suggestion(
                    hmsNr = hmsNr,
                )
            }
            suggestions[hmsNr]!!.occurancesInSoknader++
        }

        return suggestions.toList().map { it.second }.filter { it.ready }.sortedByDescending { it.occurancesInSoknader }
    }

}
