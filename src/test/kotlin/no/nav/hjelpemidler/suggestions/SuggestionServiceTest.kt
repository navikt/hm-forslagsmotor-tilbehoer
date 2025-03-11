package no.nav.hjelpemidler.suggestions

import io.kotest.common.runBlocking
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import no.nav.hjelpemidler.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.denyList
import no.nav.hjelpemidler.github.BestillingsHjelpemiddel
import no.nav.hjelpemidler.github.Delelister
import no.nav.hjelpemidler.github.GithubClient
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.model.ProductFrontendFiltered
import no.nav.hjelpemidler.model.Suggestion
import no.nav.hjelpemidler.model.Suggestions
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.service.hmdb.enums.Produkttype
import no.nav.hjelpemidler.service.hmdb.hentprodukter.AgreementInfoDoc
import no.nav.hjelpemidler.service.hmdb.hentprodukter.AttributesDoc
import no.nav.hjelpemidler.service.hmdb.hentprodukter.Product
import no.nav.hjelpemidler.service.hmdb.hentprodukter.ProductSupplier
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        every { githubClient.hentBestillingsordningSortiment() } returns listOf(
            BestillingsHjelpemiddel(
                "255912",
                "Ispigg krykke albue Classic",
                null,
            ),
        )
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
        assertTrue(tilbehør.erPåBestillingsordning!!)
    }

    @Test
    fun `hentTilbehør skal returnere tilbehør med erPåBestillingsordning=false hvis det ikke er i bestillingssortiment`() =
        runBlocking {
            val tilbehør = suggestionService.hentTilbehør("000000", hmsnrHovedprodukt)
            assertNull(tilbehør.error)
            assertFalse(tilbehør.erPåBestillingsordning!!)
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
                    tilgjengeligForDigitalSoknad = true,
                ),
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
                    produkttype = Produkttype.HOVEDPRODUKT,
                ),
            )

            val tilbehør = suggestionService.hentTilbehør(hmsnrTilbehør, hmsnrHovedprodukt)
            assertEquals(TilbehørError.IKKE_TILGJENGELIG_DIGITALT, tilbehør.error)
        }

    @Test
    fun `hentTilbehør skal returnere error fra denyList dersom tilbehør ligger i denyList`() = runBlocking {
        val hmsnrIkkePåRammeavtale = denyList.entries.first { it.value == TilbehørError.IKKE_PÅ_RAMMEAVTALE }.key
        val tilbehør = suggestionService.hentTilbehør(hmsnrIkkePåRammeavtale, hmsnrHovedprodukt)
        assertEquals(TilbehørError.IKKE_PÅ_RAMMEAVTALE, tilbehør.error)
    }

    @Test
    fun `hentTilbehør skal sette erSelvforklarendeTilbehør true for standard tilbehør`() = runBlocking {
        every { suggestionEngine.suggestions(hmsnrHovedprodukt) } returns Suggestions(
            dataStartDate = null,
            suggestions = listOf(
                Suggestion(hmsNr = hmsnrTilbehør, title = "Trekk inko Hypnos X"),
                Suggestion(hmsNr = hmsnrTilbehørOgReservedel, title = "Ispigg krykke xxx")
            )
        )
        every { suggestionEngine.deleteSuggestions(any()) } just runs
        coEvery { hjelpemiddeldatabaseClient.hentProdukter(any<Set<String>>()) } returns listOf()
        val suggestionsFrontendFiltered = suggestionService.suggestions(hmsnrHovedprodukt)
        assertEquals(true, suggestionsFrontendFiltered.suggestions[0].erSelvforklarendeTilbehør)
        assertEquals(false, suggestionsFrontendFiltered.suggestions[1].erSelvforklarendeTilbehør)
    }

    @Test
    fun `introspection skal returnere riktig sortering`() = runBlocking {
        coEvery { suggestionEngine.introspect() } returns listOf(
            productFrontendFiltered(
                "123456",
                listOf(
                    suggestion("333333", 234),
                    suggestion("222222", 12),
                    suggestion("111111", 5),
                ),
            ),
        )
        coEvery { hjelpemiddeldatabaseClient.hentProdukter(any<Set<String>>()) } returns listOf(
            produkt(
                "222222",
                tilgjengeligForDigitalSoknad = true,
            ),
        )
        val introspecton = suggestionService.introspect()!!
        assertEquals(2, introspecton.first().suggestions.size)
        assertEquals(234, introspecton.first().suggestions[0].occurancesInSoknader)
        assertEquals(5, introspecton.first().suggestions[1].occurancesInSoknader)
    }

    @Test
    fun `skal vise forslag basert på info fra grunndata`() {
        val hmsnrTilbehørPåRammeavtale = "111111"
        val hmsnrTilbehørIkkePåRammeavtale = "222222"
        val hmsnrIkkeTilbehør = "333333"
        val forslag =
            Suggestions(
                dataStartDate = null,
                suggestions = listOf(
                    Suggestion(hmsnrTilbehørPåRammeavtale),
                    Suggestion(hmsnrTilbehørIkkePåRammeavtale),
                    Suggestion(hmsnrIkkeTilbehør)
                )
            )
        val grunndataTilbehørprodukter = listOf(
            produkt(hmsnrTilbehørPåRammeavtale, accessory = true, hasAgreement = true),
            produkt(hmsnrTilbehørIkkePåRammeavtale, accessory = true, hasAgreement = false),
            produkt(hmsnrIkkeTilbehør, accessory = false, hasAgreement = true),
        )
        val (skalVises, skalIkkeVises) = suggestionService.splittForslagbasertPåVisning(
            forslag,
            grunndataTilbehørprodukter,
            deleliste(),
            produkt(hmsnrHovedprodukt)
        )
        assertEquals(hmsnrTilbehørPåRammeavtale, skalVises.first().hmsNr)
        assertEquals(hmsnrTilbehørIkkePåRammeavtale, skalIkkeVises[0].hmsNr)
        assertEquals(hmsnrIkkeTilbehør, skalIkkeVises[1].hmsNr)
    }

    private fun deleliste(vararg hmsnrs: String): Delelister =
        mapOf(rammeavtaleId to mapOf(leverandørId to setOf(*hmsnrs)))

    private fun produkt(
        hmsnr: String,
        tilgjengeligForDigitalSoknad: Boolean = false,
        produkttype: Produkttype? = null,
        accessory: Boolean = false,
        hasAgreement: Boolean = true,
    ) = Product(
        hmsArtNr = hmsnr,
        attributes = AttributesDoc(digitalSoknad = tilgjengeligForDigitalSoknad, produkttype = produkttype),
        supplier = ProductSupplier(id = leverandørId),
        main = true,
        agreements = listOf(
            AgreementInfoDoc(
                id = rammeavtaleId,
            ),
        ),
        accessory = accessory,
        hasAgreement = hasAgreement,
    )

    private fun productFrontendFiltered(hmsnr: String, suggestions: List<Suggestion> = emptyList()) =
        ProductFrontendFiltered(
            hmsnr = hmsnr,
            title = hmsnr,
            suggestions = suggestions,
            frameworkAgreementStartDate = LocalDate.now(),
        )

    private fun suggestion(hmsnr: String, occurences: Int) =
        Suggestion(hmsNr = hmsnr, title = hmsnr, occurancesInSoknader = occurences)
}
