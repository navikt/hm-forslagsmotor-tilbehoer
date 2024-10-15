package no.nav.hjelpemidler

/**
 * Tilbehør som hverken skal dukke opp som forslag eller som skal kunne legges til manuelt
 * i digital behovsmelding.
 */
val denyList = setOf(
    "215124", "215125", // Standardgrinder til Opus 90 og 120 bredde, som følger med hovedhjelpemiddelet
    "167689", "203278", // Standardutstyr Holder krykke ers Orion Pro 4W/Comet Alpine+/Comet Ultra
    "292412", // Rensevæske personløfter badekar Bellavita el (forbruksvare som ikke dekkes av NAV)
    "189518", // Gripo Spesial for takhøyde <210cm. Finnes ikke på rammeavtale og blir dermed ikke stoppet som hovedhjelpemiddel
    "244433", // 'Monteringssett toalett Aquatec 9000 vegghengt' Aquatec forhøyer som ikke er på avtale lenger
    "296408", // 'Ryggsekk ers Eloflex' - Dette er en standard sekk NAV ikke dekker.
    "144149", // Hjelpemiddel: Glidelaken Arcus Vita glideflate
    "214262", // Krevert tilleggsskjema. Individuell tilpasning timespris ny/brukt varmehjm Multishell/Dartex/Tynset/Mikko/HeliSki/Roller/Tolga/Tallberg/Handy Aktiv/HeliSki jr./ Goretex jr. /Budor/Trysil/Herkules/Monty/Splitt
    "311284", "311285", "311286", "301593", // Avtalt med KBA da de ikke er godkjent i avtalen for ers.
    "199692", // Tilhører forrige avtale. Sete ergonomisk ekstra mykt dusjtoastol Aquatec Ocean/Ocean VIP/Ocean VIP XL/Ocean XL/Ocean E-Vip
    "243389", "196626", // Roger høyrselshjelpemiddel som er hovedprodukt
    "288941", // Toalettsete. Dusj -og toalettstoler som har dette setet som tilbehør, får det også levert med det som standard. Trengs derfor ikke å foreslås som tilbehør.
    "214199", // Seil Molift Evosling hygiene medium. Ikke på rammeavtale
    "323586", // Fremdriftshjelp stol Vela. "Til informasjon har vi nå mottatt beskjed om at NAV har trukket 323586 Fremdriftshjelp stol Vela  tilbake som et tilbehør"
)
