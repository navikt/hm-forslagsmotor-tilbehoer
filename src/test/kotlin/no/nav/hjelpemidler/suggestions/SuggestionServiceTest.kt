package no.nav.hjelpemidler.suggestions

import io.kotest.common.runBlocking
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.denyList
import no.nav.hjelpemidler.github.Delelister
import no.nav.hjelpemidler.github.GithubClient
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.service.hmdb.enums.Produkttype
import no.nav.hjelpemidler.service.hmdb.hentprodukter.Produkt
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SuggestionServiceTest {

    private val suggestionEngine = mockk<SuggestionEngine>()
    private val aivenMetrics = mockk<AivenMetrics>(relaxed = true)

    private val githubClient = mockk<GithubClient>()
    private val hjelpemiddeldatabaseClient = mockk<HjelpemiddeldatabaseClient>()
    private val oebs = mockk<Oebs>()
    private val suggestionService =
        SuggestionService(suggestionEngine, aivenMetrics, hjelpemiddeldatabaseClient, githubClient, oebs)

    private val hmsnrHovedprodukt = "123456"
    private val hmsnrTilbehør = "255912"
    private val hmsnrReservedel = "255914"
    private val hmsnrTilbehørOgReservedel = "255911"
    private val rammeavtaleId = "8590"
    private val leverandørId = "5010"

    init {
        every { githubClient.hentTilbehørslister() } returns deleliste(hmsnrTilbehørOgReservedel, hmsnrTilbehør)
        every { githubClient.hentReservedelslister() } returns deleliste(hmsnrTilbehørOgReservedel, hmsnrReservedel)
        every { suggestionEngine.cachedTitleAndTypeFor(any()) } returns null
        every { oebs.getTitleForHmsNr(any()) } returns Pair("tittel", "type")
        coEvery { hjelpemiddeldatabaseClient.hentProdukter(any<String>()) } returns emptyList()
        coEvery { hjelpemiddeldatabaseClient.hentProdukter(hmsnrHovedprodukt) } returns listOf(produkt(hmsnrHovedprodukt))
    }

    @Test
    fun `hentTilbehør skal returnere tilbehør`() = runBlocking {
        val tilbehør = suggestionService.hentTilbehør(hmsnrTilbehør, hmsnrHovedprodukt)
        assertNull(tilbehør.error)
        assertTrue(tilbehør.name!!.isNotBlank())
    }

    @Test
    fun `hentTilbehør skal returnere IKKE_FUNNET dersom noe feiler`() = runBlocking {
        every { oebs.getTitleForHmsNr(any()) } throws RuntimeException("OEBS er nede for vedlikehold")
        val tilbehør = suggestionService.hentTilbehør(hmsnrTilbehør, hmsnrHovedprodukt)
        assertEquals(TilbehørError.IKKE_FUNNET, tilbehør.error)
    }

    @Test
    fun `hentTilbehør skal returnere IKKE_TILBEHØR dersom hmsnr er tilgjengelig som hovedprodukt i digital behovsmelding`() =
        runBlocking {
            coEvery { hjelpemiddeldatabaseClient.hentProdukter(hmsnrTilbehør) } returns listOf(
                produkt(
                    hmsnrTilbehør,
                    tilgjengeligForDigitalSoknad = true
                )
            )

            val tilbehør = suggestionService.hentTilbehør(hmsnrTilbehør, hmsnrHovedprodukt)
            assertEquals(TilbehørError.IKKE_ET_TILBEHØR, tilbehør.error)
        }

    @Test
    fun `hentTilbehør skal returnere IKKE_TILGJENGELIG_DIGITALT dersom hmsnr er hovedprodukt som ikke er i digital behovsmelding`() =
        runBlocking {
            coEvery { hjelpemiddeldatabaseClient.hentProdukter(hmsnrTilbehør) } returns listOf(
                produkt(
                    hmsnrTilbehør,
                    produkttype = Produkttype.HOVEDPRODUKT
                )
            )

            val tilbehør = suggestionService.hentTilbehør(hmsnrTilbehør, hmsnrHovedprodukt)
            assertEquals(TilbehørError.IKKE_TILGJENGELIG_DIGITALT, tilbehør.error)
        }

    @Test
    fun `hentTilbehør skal returnere IKKE_TILGJENGELIG_DIGITALT dersom hmsnr ligger i denyList`() = runBlocking {
        val tilbehør = suggestionService.hentTilbehør(denyList.first(), hmsnrHovedprodukt)
        assertEquals(TilbehørError.IKKE_TILGJENGELIG_DIGITALT, tilbehør.error)
    }

    /** Kommentert ut inntil vi gjør mer enn å bare logge når det forsøkes å legge til reservedel som tilbehør
     @Test
     fun `hentTilbehør skal returnere RESERVEDEL dersom hmsnr er i reservedelsliste, men ikke i tilbehørsliste`() =
     runBlocking {
     coEvery { hjelpemiddeldatabaseClient.hentProdukter(hmsnrReservedel) } returns listOf(produkt(hmsnrReservedel))
     val tilbehør = suggestionService.hentTilbehør(hmsnrReservedel)
     assertEquals(TilbehørError.RESERVEDEL, tilbehør.error)
     }

     @Test
     fun `hentTilbehør skal returnere tilbehør dersom hmsnr er både i reservedelsliste og i tilbehørsliste`() =
     runBlocking {
     coEvery { hjelpemiddeldatabaseClient.hentProdukter(hmsnrTilbehørOgReservedel) } returns listOf(
     produkt(
     hmsnrTilbehørOgReservedel
     )
     )
     val tilbehør = suggestionService.hentTilbehør(hmsnrTilbehørOgReservedel)
     assertNull(tilbehør.error)
     assertTrue(tilbehør.name!!.isNotBlank())
     }
     */

    private fun deleliste(vararg hmsnrs: String): Delelister =
        mapOf(rammeavtaleId to mapOf(leverandørId to setOf(*hmsnrs)))

    private fun produkt(
        hmsnr: String,
        tilgjengeligForDigitalSoknad: Boolean = false,
        produkttype: Produkttype? = null
    ) = Produkt(
        id = "",
        hmsnr = hmsnr,
        artikkelId = "",
        artikkelnavn = "",
        produktId = "",
        produktbeskrivelse = "",
        isotittel = "",
        blobUrlLite = "",
        rammeavtaleStart = "",
        rammeavtaleSlutt = "",
        tilgjengeligForDigitalSoknad = tilgjengeligForDigitalSoknad,
        produkttype = produkttype,
        rammeavtaleId = rammeavtaleId,
        leverandorId = leverandørId,
    )
}
