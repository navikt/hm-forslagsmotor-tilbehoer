package no.nav.hjelpemidler.db

import io.mockk.mockk
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.model.Hjelpemiddel
import no.nav.hjelpemidler.model.HjelpemiddelListe
import no.nav.hjelpemidler.model.Soknad
import no.nav.hjelpemidler.model.SoknadData
import no.nav.hjelpemidler.model.Tilbehoer
import no.nav.hjelpemidler.suggestions.SuggestionEnginePostgres
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class SuggestionEngineTest {

    private val aivenMetricsMock = mockk<AivenMetrics>(relaxed = true)

    @Test
    fun `No suggestions available for item`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance, aivenMetricsMock).apply {
                this.testInjectCacheHmdb("1234", null, null)
                val suggestions = this.suggestions("1234")
                assertEquals(0, suggestions.suggestions.count())
            }
        }
    }

    @Test
    fun `Fewer occurrences than five results in no result, five or more occurrences results in results`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance, aivenMetricsMock).apply {

                this.testInjectCacheHmdb("1234", null, null)
                this.testInjectCacheHmdb("54321", null, null)
                this.testInjectCacheOebs("4321", "Tilbehør 1", "Hjelpemiddel")
                this.testInjectCacheOebs("12345", "Tilbehør 2", "Hjelpemiddel")

                // Process applications to generate suggestions
                this.processApplications(
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
                        )
                    )
                )

                // Request suggestions
                val suggestions = this.suggestions("1234")

                // Assertions
                assertEquals(0, suggestions.suggestions.count())

                // Request suggestions
                val suggestions2 = this.suggestions("54321")

                // Assertions
                assertEquals(1, suggestions2.suggestions.count())
                assertEquals("12345", suggestions2.suggestions[0].hmsNr)
                assertEquals("Tilbehør 2", suggestions2.suggestions[0].title)
                assertEquals(5, suggestions2.suggestions[0].occurancesInSoknader)

                // Clean up background runner
                this.close()
            }
        }
    }

    @Test
    fun `A single suggestion is available based on five or more occurrences`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance, aivenMetricsMock).apply {

                this.testInjectCacheHmdb("1234", null, null)
                this.testInjectCacheOebs("4321", "Tilbehør 1", "Hjelpemiddel")

                // Process applications to generate suggestions
                this.processApplications(
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
                    )
                )

                // Request suggestions
                val suggestions = this.suggestions("1234")

                // Assertions
                assertEquals(1, suggestions.suggestions.count())
                assertEquals("4321", suggestions.suggestions[0].hmsNr)
                assertEquals("Tilbehør 1", suggestions.suggestions[0].title)
                assertEquals(5, suggestions.suggestions[0].occurancesInSoknader)

                // Clean up background runner
                this.close()
            }
        }
    }

    @Test
    fun `Multiple suggestions and priority is correct`() {
        withMigratedDb {
            SuggestionEnginePostgres(DataSource.instance, aivenMetricsMock).apply {

                this.testInjectCacheHmdb("1234", null, null)
                this.testInjectCacheOebs("4321", "Tilbehør 1", "Hjelpemiddel")
                this.testInjectCacheOebs("5678", "Tilbehør 2", "Hjelpemiddel")

                // Process applications to generate suggestions
                this.processApplications(
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
                    )
                )
                this.processApplications(
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
                )

                // Request suggestions
                val suggestions = this.suggestions("1234")

                // Assertions
                assertEquals(2, suggestions.suggestions.count())
                assertEquals("5678", suggestions.suggestions[0].hmsNr)
                assertEquals("Tilbehør 2", suggestions.suggestions[0].title)
                assertEquals(6, suggestions.suggestions[0].occurancesInSoknader)

                // Clean up background runner
                this.close()
            }
        }
    }
}
