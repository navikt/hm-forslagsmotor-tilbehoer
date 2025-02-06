package no.nav.hjelpemidler

import no.nav.hjelpemidler.github.Hmsnr
import no.nav.hjelpemidler.suggestions.TilbehørError

/**
 * Tilbehør som hverken skal dukke opp som forslag eller som skal kunne legges til manuelt i digital behovsmelding.
 */
val denyList: Map<Hmsnr, TilbehørError> = mapOf(
    TilbehørError.HOVEDHJELPEMIDDEL to setOf(
        "215124", "215125", // Standardgrinder til Opus 90 og 120 bredde, som følger med hovedhjelpemiddelet
        "167689", "203278", // Standardutstyr Holder krykke ers Orion Pro 4W/Comet Alpine+/Comet Ultra
        "144149", // Hjelpemiddel: Glidelaken Arcus Vita glideflate
        "243389", "196626", // Roger høyrselshjelpemiddel som er hovedprodukt
    ),

    TilbehørError.DEKKES_IKKE_AV_NAV to setOf(
        "292412", // Rensevæske personløfter badekar Bellavita el (forbruksvare som ikke dekkes av NAV)
        "296408", // 'Ryggsekk ers Eloflex' - Dette er en standard sekk NAV ikke dekker.
        "241521", // Batteribackup stol oppreis Nova 20/30. Vi gir generelt ikke batteribackup
    ),

    TilbehørError.IKKE_PÅ_RAMMEAVTALE to setOf(
        "244433", // 'Monteringssett toalett Aquatec 9000 vegghengt' Aquatec forhøyer som ikke er på avtale lenger
        "311284", "311285", "311286", "301593", // Avtalt med KBA da de ikke er godkjent i avtalen for ers.
        "199692", // Tilhører forrige avtale. Sete ergonomisk ekstra mykt dusjtoastol Aquatec Ocean/Ocean VIP/Ocean VIP XL/Ocean XL/Ocean E-Vip
        "214199", // Seil Molift Evosling hygiene medium. Ikke på rammeavtale
        "323586", // Fremdriftshjelp stol Vela. "Til informasjon har vi nå mottatt beskjed om at NAV har trukket 323586 Fremdriftshjelp stol Vela  tilbake som et tilbehør"
        "100863", // Lasal Bananpute 1 m/trekk. Ikke på rammeavtale
    ),

    TilbehørError.TJENESTE to setOf(
        "214262", // Krevert tilleggsskjema. Individuell tilpasning timespris ny/brukt varmehjm Multishell/Dartex/Tynset/Mikko/HeliSki/Roller/Tolga/Tallberg/Handy Aktiv/HeliSki jr./ Goretex jr. /Budor/Trysil/Herkules/Monty/Splitt
    ),

    TilbehørError.LEVERES_SOM_DEL_AV_HJELPEMIDDELET to setOf(
        "288941", // Toalettsete. Dusj -og toalettstoler som har dette setet som tilbehør, får det også levert med det som standard. Trengs derfor ikke å foreslås som tilbehør.
    )
).inverter() // Inverter fra redigeringsvennlig format til programmeringsvennlig format

private fun Map<TilbehørError, Set<Hmsnr>>.inverter(): Map<Hmsnr, TilbehørError> =
    this.flatMap { (error, hmsnrs) -> hmsnrs.map { it to error } }.toMap()
