package no.nav.hjelpemidler.suggestionengine

object SuggestionEngine {
    private val items = mutableMapOf<String, Item>()

    init {
        println("Loading initial dataset for Suggestion Engine.")
        // TODO: load initial dataset
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
        return items[hmsNr]?.suggestions?.map { it.value }?.sortedByDescending { it.occurancesInSoknader } ?: listOf()
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
