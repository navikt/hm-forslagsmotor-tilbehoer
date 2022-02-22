package no.nav.hjelpemidler.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.hjelpemidler.suggestionengine.Hjelpemiddel
import no.nav.hjelpemidler.suggestionengine.Soknad
import no.nav.hjelpemidler.suggestionengine.Suggestion
import no.nav.hjelpemidler.suggestionengine.Tilbehoer
import org.postgresql.util.PGobject
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

private val MIN_OCCURANCES = 4

internal interface SoknadStore {
    fun suggestions(hmsnr: String): List<Suggestion>
    fun processApplication(soknad: Soknad)
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {
    companion object {
        val ApplicationPreviouslyProcessedException = RuntimeException("application previously processed")
    }

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    override fun suggestions(hmsnr: String): List<Suggestion> =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        SELECT * FROM (
                            SELECT
                                sc.hmsnr_tilbehoer,
                                sum(sc.quantity) AS occurances,
                                o.title,
                                h.framework_agreement_start,
                                h.framework_agreement_end
                            FROM v1_score_card AS sc
                            LEFT JOIN v1_cache_hmdb AS h ON h.hmsnr = sc.hmsnr_hjelpemiddel
                            INNER JOIN v1_cache_oebs AS o ON o.hmsnr = sc.hmsnr_tilbehoer
                            WHERE
                                sc.hmsnr_hjelpemiddel = ?
                                
                                -- We do not include results where we do not have a cached title for it (yet)
                                AND o.title IS NOT NULL
                        
                                -- If the product is currently on a framework agreement, only include records with "created" date newer than the
                                -- framework_agreement_start-date.
                                AND NOT (
                                    h.framework_agreement_start IS NOT NULL AND
                                    h.framework_agreement_end IS NOT NULL AND
                                    (h.framework_agreement_start <= NOW() AND h.framework_agreement_end >= NOW()) AND
                                    sc.created < h.framework_agreement_start
                                )
                        
                                -- Remove illegal suggestions from results
                                AND (
                                    SELECT 1 FROM v1_illegal_suggestion WHERE hmsnr_hjelpemiddel = sc.hmsnr_hjelpemiddel AND hmsnr_tilbehoer = sc.hmsnr_tilbehoer
                                ) IS NULL
                        
                            GROUP BY sc.hmsnr_tilbehoer, h.framework_agreement_start, h.framework_agreement_end, o.title
                            ORDER BY occurances DESC
                        ) AS q
                        WHERE q.occurances > ?
                        ;
                    """.trimIndent(),
                    hmsnr,
                    MIN_OCCURANCES,
                ).map {
                    Suggestion(
                        hmsNr = it.string("hmsnr"),
                        title = it.string("title"),
                        occurancesInSoknader = it.int("occurances"),
                    )
                }.asList
            )
        }

    override fun processApplication(soknad: Soknad) {
        logg.info("DEBUG: processApplication: processing soknads_id=${soknad.soknad.id}")
        var oebsCacheStale = false
        var hmdbCacheStale = false

        // Contain logic that has to succeed together in a database transaction
        using(sessionOf(ds)) { session ->
            session.transaction { transaction ->
                // Insert soknad into v1_soknad
                var rowsEffected = transaction.run(
                    queryOf(
                        """
                            INSERT INTO v1_soknad (soknads_id, data, created)
                            VALUES
                                (?, ?, ?)
                            ON CONFLICT DO NOTHING
                            ;
                        """.trimIndent(),
                        soknad.soknad.id,
                        PGobject().apply {
                            type = "jsonb"
                            value = objectMapper.writeValueAsString(soknad)
                        },
                        soknad.created,
                    ).asUpdate
                )

                logg.info("DEBUG: processApplication: inserting soknad: rowsEffected=$rowsEffected")

                if (rowsEffected == 0) {
                    // We have already seen this application and did not add anything, lets stop processing here
                    throw ApplicationPreviouslyProcessedException // Note: transaction rollback
                }

                // Add one or more score-cards to v1_score_card with accessory-to-product relations
                val productsAppliedForWithAccessories = soknad.soknad.hjelpemidler.hjelpemiddelListe
                    .groupBy { it.hmsNr }
                    .filter { it.value.isNotEmpty() }
                    .mapValues {
                        // Map a list of products with the same hmsnr into a single value with their combined list of accessories
                        val accessories = it.value.fold(mutableListOf<Tilbehoer>()) { a, b ->
                            a.addAll(b.tilbehorListe)
                            a
                        }
                        Hjelpemiddel(
                            hmsNr = it.key,
                            tilbehorListe = accessories,
                        )
                    }
                    .filter { it.value.tilbehorListe.isNotEmpty() }

                if (productsAppliedForWithAccessories.count() == 0) {
                    // Nothing more to do as there were no products with accessories applied for
                    return@transaction
                }

                for ((sid, p) in productsAppliedForWithAccessories) {
                    logg.info("DEBUG: processApplication: product: $sid")
                    for ((aid, a) in p.tilbehorListe) logg.info("DEBUG: processApplication: acessory: $aid")
                }

                for ((product_hmsnr, product) in productsAppliedForWithAccessories) {
                    for ((accessory_hmsnr, accessory) in product.tilbehorListe.groupBy { it.hmsnr }) {
                        logg.info("DEBUG: processApplication: inserting v1_score_card row: pid=$product_hmsnr aid=$accessory_hmsnr sid=${soknad.soknad.id} quantity=${accessory.count()} created=${soknad.created}")
                        rowsEffected = transaction.run(
                            queryOf(
                                """
                                    INSERT INTO v1_score_card (hmsnr_hjelpemiddel, hmsnr_tilbehoer, soknads_id, quantity, created)
                                    VALUES
                                        (?, ?, ?, ?, ?)
                                    ;
                                """.trimIndent(),
                                product_hmsnr,
                                accessory_hmsnr,
                                soknad.soknad.id,
                                accessory.count(),
                                soknad.created,
                            ).asUpdate
                        )

                        if (rowsEffected != 1) {
                            throw RuntimeException("unexpected number of rows effected: actual=$rowsEffected != expected=1") // Note: transaction rollback
                        }
                    }
                }

                // Add products hmsnrs' to v1_cache_hmdb if does not already exist
                for (hmsnr in productsAppliedForWithAccessories.keys) {
                    logg.info("DEBUG: processApplication: inserting v1_cache_hmdb row: pid=$hmsnr framework_agreement_start=null framework_agreement_end=null")
                    rowsEffected = transaction.run(
                        queryOf(
                            """
                                INSERT INTO v1_cache_hmdb (hmsnr, framework_agreement_start, framework_agreement_end)
                                VALUES
                                    (?, ?, ?)
                                ON CONFLICT DO NOTHING
                                ;
                            """.trimIndent(),
                            hmsnr,
                            null, // TODO: fix me
                            null, // TODO: fix me
                        ).asUpdate
                    )
                    if (rowsEffected > 0) {
                        // Make a note of any rows added to start a fetch from HMDB after this.
                        hmdbCacheStale = true
                    }
                }

                // Add products and accessories applied for to v1_cache_oebs if does not already exist
                for ((product_hmsnr, product) in productsAppliedForWithAccessories) {
                    logg.info("DEBUG: processApplication: inserting v1_cache_oebs row: pid=$product_hmsnr title=null type='Hjelpemiddel'")
                    rowsEffected = transaction.run(
                        queryOf(
                            """
                                INSERT INTO v1_cache_oebs (hmsnr, title, type)
                                VALUES
                                    (?, NULL, 'Hjelpemiddel')
                                ON CONFLICT DO NOTHING
                                ;
                            """.trimIndent(),
                            product_hmsnr,
                        ).asUpdate
                    )

                    if (rowsEffected > 0) {
                        // Make a note of any rows added to start a fetch from OEBS after this.
                        oebsCacheStale = true
                    }

                    for (accessory in product.tilbehorListe) {
                        logg.info("DEBUG: processApplication: inserting v1_cache_oebs row: aid=${accessory.hmsnr} title=null type='Tilbehoer'")
                        rowsEffected = transaction.run(
                            queryOf(
                                """
                                INSERT INTO v1_cache_oebs (hmsnr, title, type)
                                VALUES
                                    (?, NULL, 'Tilbehoer')
                                ON CONFLICT DO NOTHING
                                ;
                                """.trimIndent(),
                                accessory.hmsnr,
                            ).asUpdate
                        )

                        if (rowsEffected > 0) {
                            // Make a note of any rows added to start a fetch from OEBS after this.
                            oebsCacheStale = true
                        }
                    }
                }
            }
        }

        // TODO: Regenerate stats for grafana (all or only changes?)
    }
}
