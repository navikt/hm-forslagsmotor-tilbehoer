package no.nav.hjelpemidler.suggestionengine

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Suggestion(
    val hmsNr: String,
    val title: String? = null,
    val dataStartDate: LocalDate? = null,

    var occurancesInSoknader: Int = 0,
) {
    fun isReady(): Boolean {
        return hmsNr.isNotEmpty() && !title.isNullOrBlank()
    }

    fun toFrontendFiltered(): SuggestionFrontendFiltered {
        return SuggestionFrontendFiltered(hmsNr, title ?: "", dataStartDate)
    }
}

data class SuggestionFrontendFiltered(
    val hmsNr: String,
    val title: String,
    val dataStartDate: LocalDate? = null,
)

data class Soknad(
    val soknad: SoknadData,
    var created: LocalDateTime?,
)

data class SoknadData(
    val id: UUID,
    val hjelpemidler: HjelpemiddelListe
)

data class HjelpemiddelListe(
    val hjelpemiddelListe: List<Hjelpemiddel>,
)

data class Hjelpemiddel(
    val hmsNr: String,
    val tilbehorListe: List<Tilbehoer>,
)

data class Tilbehoer(
    val hmsnr: String,
    val antall: Int,
    val navn: String,
    val brukAvForslagsmotoren: BrukAvForslagsmotoren?,
)

data class BrukAvForslagsmotoren(
    val lagtTilFraForslagsmotoren: Boolean,
    val oppslagAvNavn: Boolean,
)
