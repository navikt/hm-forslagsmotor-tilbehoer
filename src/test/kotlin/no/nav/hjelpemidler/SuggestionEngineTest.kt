package no.nav.hjelpemidler

import no.nav.hjelpemidler.suggestionengine.Hjelpemiddel
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine
import no.nav.hjelpemidler.suggestionengine.Tilbehoer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class SuggestionEngineTest {
    @Test
    fun `No suggestions available for item`() {
        val suggestions = SuggestionEngine.suggestionsForHmsNr("1234")
        assertEquals(suggestions.size, 0)
    }

    @Test
    fun `A single suggestion is available`() {
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
        assertEquals(suggestions.size, 1)
        assertEquals(suggestions[0].hmsNr, "4321")
        assertEquals(suggestions[0].title, "Tilbehør 1")
        assertEquals(suggestions[0].occurancesInSoknader, 1)
    }

    @Test
    fun `Multiple suggestions and priority is correct`() {
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
        assertEquals(suggestions.size, 2)
        assertEquals(suggestions[0].hmsNr, "5678")
        assertEquals(suggestions[0].title, "Tilbehør 2")
        assertEquals(suggestions[0].occurancesInSoknader, 2)
    }
}
