package no.nav.hjelpemidler.github

data class BestillingsHjelpemiddel(
    val hmsnr: String,
    val navn: String,
    val tilbehor: List<String>?
)

typealias Rammeavtaler = Map<RammeavtaleId, Leverandører>

typealias Leverandører = Map<LeverandørId, List<Hmsnr>> // TODO listen burde være et set

typealias Hmsnr = String

typealias LeverandørId = String

typealias RammeavtaleId = String