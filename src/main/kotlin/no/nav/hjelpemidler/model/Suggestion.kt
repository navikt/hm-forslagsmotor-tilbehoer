package no.nav.hjelpemidler.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class Suggestion(
    val hmsNr: String,
    val title: String? = null,

    var occurancesInSoknader: Int = 0,
) {
    @JsonIgnore
    fun toFrontendFiltered(): SuggestionFrontendFiltered {
        return SuggestionFrontendFiltered(hmsNr, title ?: "")
    }
}

data class SuggestionFrontendFiltered(
    val hmsNr: String,
    val title: String,
)

data class Suggestions(
    val dataStartDate: LocalDate?,
    var suggestions: List<Suggestion>,
)

data class SuggestionsFrontendFiltered(
    val dataStartDate: LocalDate?,
    val suggestions: List<SuggestionFrontendFiltered>,
)
