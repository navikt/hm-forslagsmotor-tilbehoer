package no.nav.hjelpemidler.db

import junit.framework.Assert.assertEquals
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
    private fun testHelper(store: SuggestionEnginePostgres) {
        store.testInjectCacheHmdb("014112", null, null)
        store.testInjectCacheOebs("000001", "Tilbehoer 1", "Hjelpemiddel")
    }

    @Test
    fun `No suggestions available due too only four occurances`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance).apply {
                testHelper(this)

                // Process applications to generate suggestions
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
                        ),
                    )
                )

                // Request suggestions
                val results = this.suggestions("014112")

                // Assertions
                assert(results.suggestions.isEmpty())
            }
        }
    }

    @Test
    fun `One suggestion available with five occurances`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance).apply {
                testHelper(this)

                // Process applications to generate suggestions
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
                        ),
                    )
                )

                // Request suggestions
                val results = this.suggestions("014112")

                // Assertions
                assertEquals(1, results.suggestions.count())
                assertEquals("000001", results.suggestions[0].hmsNr)
                assertEquals("Tilbehoer 1", results.suggestions[0].title)
                assertEquals(5, results.suggestions[0].occurancesInSoknader)
            }
        }
    }
}
