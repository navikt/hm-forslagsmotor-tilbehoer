package no.nav.hjelpemidler.model

import java.time.LocalDate

data class ProductFrontendFiltered(
    val hmsnr: String,
    val title: String,
    val suggestions: List<Suggestion>,
    val frameworkAgreementStartDate: LocalDate?,
)
