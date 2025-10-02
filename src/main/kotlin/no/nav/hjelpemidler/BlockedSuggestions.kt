package no.nav.hjelpemidler

/**
 * Set med tilbehør som ikkje skal dukke opp som forslag (ofte lagt til) tilbehør.
 * Typisk meldt inn på Teams, men utan at noen har bekreftet om tilbehøret skal fjernes helt
 * (altså legges i denyList).
 */
val blockedSuggestions = setOf(
    "313519", // Antisklimatte serveringsbrett rullator 4hjul Topro Hestia
)

/**
 * Tilbehør som er OK i seg selv, men som ofte blir lagt til på feil hjelpemiddel.
 * Legg inn ugyldige kominasjoner her for å sørge for at tilbehørene ikke dukker opp
 * som "ofte brukte tilbehør" på gitt hjelpemiddel.
 */
val blockedCombinations = mapOf<String, Set<String>>(
    "238378" to setOf( // Comet Alpine Plus
        "209683", // Presenning ers Hepro C4 universal.
    ),
)
