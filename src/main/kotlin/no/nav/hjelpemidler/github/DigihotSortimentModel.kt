package no.nav.hjelpemidler.github

import java.io.Serializable

data class BestillingsHjelpemiddel(
    val hmsnr: String,
    val navn: String,
    val tilbehor: List<String>?
) : Serializable

typealias Delelister = Map<RammeavtaleId, Leverandører>

typealias Leverandører = Map<LeverandørId, Set<Hmsnr>>

typealias Hmsnr = String

typealias LeverandørId = String

typealias RammeavtaleId = String
