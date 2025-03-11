package no.nav.hjelpemidler.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class Suggestion(
    val hmsNr: String,
    val title: String? = null,
    var occurancesInSoknader: Int = 0,
) {
    @JsonIgnore
    fun toFrontendFiltered(
        erPåBestillingsordning: Boolean,
        erPåAktivRammeavtale: Boolean?
    ): SuggestionFrontendFiltered {
        return SuggestionFrontendFiltered(
            hmsNr,
            title ?: "",
            erPåBestillingsordning = erPåBestillingsordning,
            erPåAktivRammeavtale = erPåAktivRammeavtale
        )
    }
}

data class SuggestionFrontendFiltered(
    val hmsNr: String,
    val title: String,
    val erPåBestillingsordning: Boolean,
    val erPåAktivRammeavtale: Boolean?,
) {
    val erSelvforklarendeTilbehør: Boolean = sjekkErSelvforklarende(title)
}

data class Suggestions(
    val dataStartDate: LocalDate?,
    var suggestions: List<Suggestion>,
)

data class SuggestionsFrontendFiltered(
    val dataStartDate: LocalDate?,
    val suggestions: List<SuggestionFrontendFiltered>,
)
