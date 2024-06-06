package no.nav.hjelpemidler

import no.nav.hjelpemidler.model.Suggestion
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockedSuggestionsKtTest {

    private val okArtnr = "162104"
    private val okTittel = "Polster sete myk dusjkrakk Etac Swift grått"

    private val blokkertArtnr = "244433"
    private val blokkertTittel = "Pute sete Universal mrs Crissy Swing Away/Cross 6 sb43grå"

    @Test
    fun `isBlocked skal returnere false for tilbehør som er OK`() {
        assertFalse(isBlocked(Suggestion(okArtnr, okTittel)))
    }

    @Test
    fun `isBlocked skal returnere true for blokkert artnr`() {
        assertTrue(isBlocked(Suggestion(blokkertArtnr, okTittel)))
    }

    @Test
    fun `isBlocked skal returnere true for blokkert tittel`() {
        assertTrue(isBlocked(Suggestion(okArtnr, blokkertTittel)))
    }
}
