package no.nav.hjelpemidler.model

import java.time.LocalDateTime
import java.util.UUID

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
