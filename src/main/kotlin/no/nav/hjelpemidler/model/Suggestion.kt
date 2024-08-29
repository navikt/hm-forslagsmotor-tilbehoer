package no.nav.hjelpemidler.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class Suggestion(
    val hmsNr: String,
    val title: String? = null,
    var occurancesInSoknader: Int = 0,
) {
    @JsonIgnore
    fun toFrontendFiltered(erPåBestillingsordning: Boolean): SuggestionFrontendFiltered {
        return SuggestionFrontendFiltered(hmsNr, title ?: "", erPåBestillingsordning)
    }
}

data class SuggestionFrontendFiltered(
    val hmsNr: String,
    val title: String,
    val erPåBestillingsordning: Boolean,
    val erStandardTilbehør: Boolean = sjekkErStandardTilbehør(title)
)

fun sjekkErStandardTilbehør(title: String): Boolean {
    val standardTilbehørTitles = listOf("trekk inko", "løftebøyle seng", "krykkeholder")
    val lowerCaseTitle = title.lowercase()
    return standardTilbehørTitles.any { lowerCaseTitle.startsWith(it) }
}

data class Suggestions(
    val dataStartDate: LocalDate?,
    var suggestions: List<Suggestion>,
)

data class SuggestionsFrontendFiltered(
    val dataStartDate: LocalDate?,
    val suggestions: List<SuggestionFrontendFiltered>,
)
