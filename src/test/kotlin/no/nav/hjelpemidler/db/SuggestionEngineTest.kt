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

    @Test
    fun `No suggestions available due too only four occurances`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance).apply {
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
                assertEquals(results.suggestions.count(), 1)
                assertEquals(results.suggestions[0].hmsNr, "000001")
                assertEquals(results.suggestions[0].title, "Tilbehoer 1")
                assertEquals(results.suggestions[0].occurancesInSoknader, 5)
            }
        }
    }
}
