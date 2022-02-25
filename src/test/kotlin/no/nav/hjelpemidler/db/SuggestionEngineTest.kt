package no.nav.hjelpemidler.db

import no.nav.hjelpemidler.model.Hjelpemiddel
import no.nav.hjelpemidler.model.HjelpemiddelListe
import no.nav.hjelpemidler.model.Soknad
import no.nav.hjelpemidler.model.SoknadData
import no.nav.hjelpemidler.model.Tilbehoer
import no.nav.hjelpemidler.suggestionengine.SuggestionEnginePostgres
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class SuggestionEngineTest {

    @Test
    fun `Test container test`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance).apply {
                this.processApplications(
                    listOf(
                        Soknad(
                            soknad = SoknadData(
                                id = UUID.randomUUID(),
                                hjelpemidler = HjelpemiddelListe(
                                    hjelpemiddelListe = listOf(
                                        Hjelpemiddel(
                                            hmsNr = "014112",
                                            tilbehorListe = listOf(
                                                Tilbehoer(
                                                    hmsnr = "000001",
                                                    antall = 1,
                                                    navn = "Tilbehoer 1",
                                                    brukAvForslagsmotoren = null,
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            created = LocalDateTime.now(),
                        )
                    )
                )
            }
        }
        /* withMigratedDb {
            HotsakStorePostgres(DataSource.instance).apply {
                this.lagKnytningMellomSakOgSøknad(hotsakTilknytningData)
                val søknad: VedtaksresultatHotsakData? = this.hentVedtaksresultatForSøknad(søknadId)
                assertEquals("1001", søknad?.saksnr)
                assertNull(søknad?.vedtaksresultat)
                assertNull(søknad?.vedtaksdato)
            }
        } */
    }

    /* withMigratedDb {
        HotsakStorePostgres(DataSource.instance).apply {
            this.lagKnytningMellomSakOgSøknad(hotsakTilknytningData)
        }
        HotsakStorePostgres(DataSource.instance).apply {
            this.lagreVedtaksresultat(søknadId, resultat, vedtaksdato)
                .also {
                    it shouldBe (1)
                }
        }
        HotsakStorePostgres(DataSource.instance).apply {
            val søknad = this.hentVedtaksresultatForSøknad(søknadId)
            assertEquals("1002", søknad?.saksnr)
            assertEquals("I", søknad?.vedtaksresultat)
            assertEquals(LocalDate.of(2021, 5, 31).toString(), søknad?.vedtaksdato.toString())
        }

        HotsakStorePostgres(DataSource.instance).apply {
            val funnetSøknadsId = this.hentSøknadsIdForHotsakNummer("1002")
            assertEquals(funnetSøknadsId, søknadId)
        }
    } */
}
