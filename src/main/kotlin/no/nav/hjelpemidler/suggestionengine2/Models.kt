package no.nav.hjelpemidler.suggestionengine2

data class Suggestion(
    val hmsNr: String,
    val title: String = "",

    var ready: Boolean = false,
    var occurancesInSoknader: Int = 0,
) {
    fun toFrontendFiltered(): SuggestionFrontendFiltered {
        return SuggestionFrontendFiltered(hmsNr, title)
    }
}

data class SuggestionFrontendFiltered(
    val hmsNr: String,
    val title: String,
)