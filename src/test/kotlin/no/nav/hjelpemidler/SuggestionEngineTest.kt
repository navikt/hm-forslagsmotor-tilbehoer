package no.nav.hjelpemidler

import no.nav.hjelpemidler.suggestionengine.Hjelpemiddel
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine
import no.nav.hjelpemidler.suggestionengine.Tilbehoer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class SuggestionEngineTest {
    @Test
    fun `No suggestions available for item`() {
        SuggestionEngine.discardDataset()
        val suggestions = SuggestionEngine.suggestionsForHmsNr("1234")
        assertEquals(0, suggestions.size)
    }

    @Test
    fun `A single suggestion is available`() {
        SuggestionEngine.discardDataset()
        SuggestionEngine.learnFromSoknad(
            listOf(
                Hjelpemiddel(
                    hmsNr = "1234",
                    tilbehorListe = listOf(
                        Tilbehoer(
                            hmsnr = "4321",
                            antall = 1,
                            navn = "Tilbehør 1",
                        )
                    ),
                )
            )
        )

        val suggestions = SuggestionEngine.suggestionsForHmsNr("1234")
        assertEquals(1, suggestions.size)
        assertEquals("4321", suggestions[0].hmsNr)
        assertEquals("Tilbehør 1", suggestions[0].title)
        assertEquals(1, suggestions[0].occurancesInSoknader)
    }

    @Test
    fun `Multiple suggestions and priority is correct`() {
        SuggestionEngine.discardDataset()
        SuggestionEngine.learnFromSoknad(
            listOf(
                Hjelpemiddel(
                    hmsNr = "1234",
                    tilbehorListe = listOf(
                        Tilbehoer(
                            hmsnr = "4321",
                            antall = 1,
                            navn = "Tilbehør 1",
                        ),
                        Tilbehoer(
                            hmsnr = "5678",
                            antall = 1,
                            navn = "Tilbehør 2",
                        ),
                    ),
                )
            )
        )

        SuggestionEngine.learnFromSoknad(
            listOf(
                Hjelpemiddel(
                    hmsNr = "1234",
                    tilbehorListe = listOf(
                        Tilbehoer(
                            hmsnr = "5678",
                            antall = 1,
                            navn = "Tilbehør 2",
                        ),
                    ),
                )
            )
        )

        val suggestions = SuggestionEngine.suggestionsForHmsNr("1234")
        assertEquals(2, suggestions.size)
        assertEquals("5678", suggestions[0].hmsNr)
        assertEquals("Tilbehør 2", suggestions[0].title)
        assertEquals(2, suggestions[0].occurancesInSoknader)
    }
}
