package no.nav.hjelpemidler

import no.nav.hjelpemidler.suggestionengine2.Hjelpemiddel
import no.nav.hjelpemidler.suggestionengine2.HjelpemiddelListe
import no.nav.hjelpemidler.suggestionengine2.Soknad
import no.nav.hjelpemidler.suggestionengine2.SoknadData
import no.nav.hjelpemidler.suggestionengine2.SuggestionEngine
import no.nav.hjelpemidler.suggestionengine2.Tilbehoer
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

internal class SuggestionEngine2Test {
    @Test
    fun `No suggestions available for item`() {
        SuggestionEngine(listOf(), mapOf(), mapOf()).use { se ->
            val suggestions = se.suggestionsForHmsNr("1234")
            assertEquals(0, suggestions.size)
        }
    }

    @Test
    fun `Fewer occurrences than five results in no result, five or more occurrences results in results`() {
        SuggestionEngine(
            listOf(
                Soknad(
                    soknad = SoknadData(
                        id = UUID.randomUUID(),
                        hjelpemidler = HjelpemiddelListe(
                            hjelpemiddelListe = listOf(
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "54321",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "12345",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "54321",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "12345",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "54321",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "12345",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "54321",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "12345",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "54321",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "12345",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                            )
                        )
                    ),
                    created = LocalDateTime.now(),
                ),
            ),
            mapOf(
                "4321" to "Tilbehør 1",
                "12345" to "Tilbehør 2",
            ),
            mapOf(),
        ).use { se ->
            val suggestions = se.suggestionsForHmsNr("1234")
            assertEquals(0, suggestions.size)

            val suggestions2 = se.suggestionsForHmsNr("54321")
            assertEquals(1, suggestions2.size)
            assertEquals("12345", suggestions2[0].hmsNr)
            assertEquals("Tilbehør 2", suggestions2[0].title)
            assertEquals(5, suggestions2[0].occurancesInSoknader)
        }
    }

    @Test
    fun `A single suggestion is available based on five or more occurrences`() {
        SuggestionEngine(
            listOf(
                Soknad(
                    soknad = SoknadData(
                        id = UUID.randomUUID(),
                        hjelpemidler = HjelpemiddelListe(
                            hjelpemiddelListe = listOf(
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        )
                                    ),
                                ),
                            )
                        )
                    ),
                    created = LocalDateTime.now(),
                ),
            ),
            mapOf(
                "4321" to "Tilbehør 1",
            ),
            mapOf(),
        ).use { se ->
            val suggestions = se.suggestionsForHmsNr("1234")
            assertEquals(1, suggestions.size)
            assertEquals("4321", suggestions[0].hmsNr)
            assertEquals("Tilbehør 1", suggestions[0].title)
            assertEquals(5, suggestions[0].occurancesInSoknader)
        }
    }

    @Test
    fun `Multiple suggestions and priority is correct`() {
        SuggestionEngine(
            listOf(
                Soknad(
                    soknad = SoknadData(
                        id = UUID.randomUUID(),
                        hjelpemidler = HjelpemiddelListe(
                            hjelpemiddelListe = listOf(
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        ),
                                        Tilbehoer(
                                            hmsnr = "5678",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        ),
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        ),
                                        Tilbehoer(
                                            hmsnr = "5678",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        ),
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        ),
                                        Tilbehoer(
                                            hmsnr = "5678",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        ),
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        ),
                                        Tilbehoer(
                                            hmsnr = "5678",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        ),
                                    ),
                                ),
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "4321",
                                            antall = 1,
                                            navn = "Tilbehør 1",
                                            brukAvForslagsmotoren = null,
                                        ),
                                        Tilbehoer(
                                            hmsnr = "5678",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    created = LocalDateTime.now(),
                ),
            ),
            mapOf(
                "4321" to "Tilbehør 1",
                "5678" to "Tilbehør 2",
            ),
            mapOf(),
        ).use { se ->
            se.learnFromSoknad(
                Soknad(
                    soknad = SoknadData(
                        id = UUID.randomUUID(),
                        hjelpemidler = HjelpemiddelListe(
                            hjelpemiddelListe = listOf(
                                Hjelpemiddel(
                                    hmsNr = "1234",
                                    tilbehorListe = listOf(
                                        Tilbehoer(
                                            hmsnr = "5678",
                                            antall = 1,
                                            navn = "Tilbehør 2",
                                            brukAvForslagsmotoren = null,
                                        ),
                                        Tilbehoer(
                                            hmsnr = "891011",
                                            antall = 1,
                                            navn = "Tilbehør 3",
                                            brukAvForslagsmotoren = null,
                                        ),
                                    ),
                                )
                            ),
                        ),
                    ),
                    created = LocalDateTime.now(),
                ),
            )
            val suggestions = se.suggestionsForHmsNr("1234")
            assertEquals(2, suggestions.size)
            assertEquals("5678", suggestions[0].hmsNr)
            assertEquals("Tilbehør 2", suggestions[0].title)
            assertEquals(6, suggestions[0].occurancesInSoknader)
        }
    }
}
