package no.nav.hjelpemidler.suggestionengine2

data class Suggestion(
    val hmsNr: String,
    val title: String? = null,

    var occurancesInSoknader: Int = 0,
) {
    fun toFrontendFiltered(): SuggestionFrontendFiltered {
        return SuggestionFrontendFiltered(hmsNr, title ?: "")
    }
    fun isReady(): Boolean {
        return hmsNr.isNotEmpty() && title != null
    }
}

data class SuggestionFrontendFiltered(
    val hmsNr: String,
    val title: String,
)
