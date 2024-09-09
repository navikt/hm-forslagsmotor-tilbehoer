package no.nav.hjelpemidler.model

/**
 * Selvforklarende tilbehør er tilbehør som er så selvsagte at det ikke er
 * behov for tilbehørsbegrunnelse når man søker om disse.
 */

fun sjekkErSelvforklarende(tilbehørtittel: String): Boolean {
    val lowercaseTittel = tilbehørtittel.lowercase()
    return selvforklarendeTilbehørTittler.any { lowercaseTittel.startsWith(it) }
}

private val selvforklarendeTilbehørTittler = listOf(
    "krykkeholder",
    "presenning",
    "seil",
    "serveringsbrett",
    "trekk inko",
)
