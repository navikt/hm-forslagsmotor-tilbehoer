package no.nav.hjelpemidler.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SelvforklarendeTilbehørTest {

    @Test
    fun `skal vurdere om tilbehør er selvforklarende`() = listOf<Pair<String, Boolean>>(
        ("Polster rygg myk dusjkrakk Etac Swift grått" to false),
        ("Bekken m/lokk dusjtoastol Ocean Ergo/Ocean Ergo Vip/Ocean Ergo 24" to true),
        ("Holder bekken dusjtoastol Etac Swift Mobil Tilt-2/Clean 24 grå" to false),
        ("Krykkeholder rullator 4hjul Volaris Kid/Volaris Patrol Wide/Volaris Patrol Wide low" to true),
    ).forEach {
        val tilbehørTittel = it.first
        val expected = it.second
        assertEquals(expected, sjekkErSelvforklarende(tilbehørTittel))
    }
}
