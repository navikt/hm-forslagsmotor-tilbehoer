package no.nav.hjelpemidler.github

data class BestillingsHjelpemiddel(
    val hmsnr: String,
    val navn: String,
    val tilbehor: List<String>?
)

typealias Delelister = Map<RammeavtaleId, Leverandører>

typealias Leverandører = Map<LeverandørId, Set<Hmsnr>>

typealias Hmsnr = String

typealias LeverandørId = String

typealias RammeavtaleId = String
