package no.nav.hjelpemidler.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.oebs.Oebs
import no.nav.hjelpemidler.soknad.db.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.suggestionengine.Hjelpemiddel
import no.nav.hjelpemidler.suggestionengine.Soknad
import no.nav.hjelpemidler.suggestionengine.Suggestion
import no.nav.hjelpemidler.suggestionengine.Tilbehoer
import org.postgresql.util.PGobject
import java.io.Closeable
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

private val MIN_OCCURANCES = 4

internal interface SoknadStore {
    fun suggestions(hmsnr: String): List<Suggestion>
    fun processApplications(soknader: List<Soknad>)
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore, Closeable {
    companion object {
        val ApplicationPreviouslyProcessedException = RuntimeException("application previously processed")
    }

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    init {
        backgroundRunner()
    }

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
                            ORDER BY occurances DESC, hmsnr_tilbehoer
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

    override fun processApplications(soknader: List<Soknad>) {
        val newHmdbRows = mutableListOf<String>()
        val newOebsRows = mutableListOf<String>()

        for (soknad in soknader) {
            runCatching {
                val (hmdb, oebs) = processApplication(soknad)
                newHmdbRows.addAll(hmdb)
                newOebsRows.addAll(oebs)
            }.getOrElse { e ->
                if (e == ApplicationPreviouslyProcessedException) {
                    logg.info("DEBUG: processApplications: ignoring ApplicationPreviouslyProcessedException")
                } else {
                    throw e
                }
            }
        }

        // If we have any new rows in caches then we fetch those specifically
        if (newHmdbRows.isNotEmpty() || newOebsRows.isNotEmpty()) {
            thread(isDaemon = true) {
                runCatching {
                    updateCaches(newHmdbRows, newOebsRows)
                }.getOrElse { e ->
                    logg.error("Failed to prefetch caches for new rows added to caches: $e")
                    e.printStackTrace()
                }
            }
        }

        // TODO: Regenerate stats for grafana (all or only changes?)
    }

    @Synchronized
    override fun close() {
        isClosed = true
    }

    // Used to stop background runner thread on close
    private var isClosed = false

    @Synchronized
    private fun isClosed(): Boolean {
        return isClosed
    }

    private fun backgroundRunner() {
        thread(isDaemon = true) {
            // Because we might run multiple pods in parallel we wait some random delay period on startup in the hope
            // that they will spread out and not run cache updates at the same time.
            var startupRandomDelaySeconds = (0..60 * 60).random()
            if (Configuration.application["APP_PROFILE"]!! == "dev") startupRandomDelaySeconds = 60
            logg.info("SoknadStore: waiting until ${LocalDateTime.now().plusSeconds(startupRandomDelaySeconds.toLong())} before we start updating caches every 60 minutes")
            Thread.sleep((1_000 * startupRandomDelaySeconds).toLong())

            var standardInterval = 60 * 60
            if (Configuration.application["APP_PROFILE"]!! == "dev") standardInterval = 60

            var firstRun = true
            while (true) {
                if (!firstRun) {
                    logg.info("Background runner sleeping until: ${LocalDateTime.now().plusSeconds(standardInterval.toLong())}")
                    Thread.sleep((1_000 * standardInterval).toLong())
                    logg.info("Background runner working..")
                }
                firstRun = false

                if (isClosed()) {
                    logg.info("Background runner exiting..")
                    return@thread
                } // We have been closed, lets clean up thread

                logg.info("Background runner launching updateCaches()..")
                runCatching {
                    updateCaches()
                }.getOrElse { e ->
                    logg.error("Background runner failed to update cache due to: $e")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun processApplication(soknad: Soknad): Pair<List<String>, List<String>> {
        logg.info("DEBUG: processApplication: processing soknads_id=${soknad.soknad.id}")

        val newHmdbRows = mutableListOf<String>()
        val newOebsRows = mutableListOf<String>()

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
                    for ((aid, _) in p.tilbehorListe) logg.info("DEBUG: processApplication: acessory: $aid")
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
                        newHmdbRows.add(hmsnr)
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
                                    (?, NULL, NULL)
                                ON CONFLICT DO NOTHING
                                ;
                            """.trimIndent(),
                            product_hmsnr,
                        ).asUpdate
                    )

                    if (rowsEffected > 0) {
                        // Make a note of any rows added to start a fetch from OEBS after this.
                        newOebsRows.add(product_hmsnr)
                    }

                    for (accessory in product.tilbehorListe) {
                        logg.info("DEBUG: processApplication: inserting v1_cache_oebs row: aid=${accessory.hmsnr} title=null type='Tilbehoer'")
                        rowsEffected = transaction.run(
                            queryOf(
                                """
                                INSERT INTO v1_cache_oebs (hmsnr, title, type)
                                VALUES
                                    (?, NULL, NULL)
                                ON CONFLICT DO NOTHING
                                ;
                                """.trimIndent(),
                                accessory.hmsnr,
                            ).asUpdate
                        )

                        if (rowsEffected > 0) {
                            // Make a note of any rows added to start a fetch from OEBS after this.
                            newOebsRows.add(accessory.hmsnr)
                        }
                    }
                }
            }
        }

        return Pair(newHmdbRows, newOebsRows)
    }

    @Synchronized
    private fun updateCaches(newHmdbRows: List<String>? = null, newOebsRows: List<String>? = null) {
        // If newHmdbRows and newOebsRows are set, then fetch them, if null we fetch a full list of items that needs polling
        val hmdbRows = if (newHmdbRows == null && newOebsRows == null) {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        """
                            SELECT hmsnr FROM v1_cache_hmdb WHERE framework_agreement_start IS NULL OR framework_agreement_end IS NULL;
                        """.trimIndent()
                    ).map {
                        it.string("hmsnr")
                    }.asList
                )
            }.toSet()
        } else {
            newHmdbRows?.toSet() ?: listOf<String>().toSet()
        }

        val oebsRows = if (newHmdbRows == null && newOebsRows == null) {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        """
                            SELECT hmsnr FROM v1_cache_oebs WHERE title IS NULL OR type IS NULL;
                        """.trimIndent()
                    ).map {
                        it.string("hmsnr")
                    }.asList
                )
            }.toSet()
        } else {
            newOebsRows?.toSet() ?: listOf<String>().toSet()
        }

        logg.info("DEBUG: updateCache: newRows=${newHmdbRows != null && newOebsRows != null} hmdbRows.count()=${hmdbRows.count()} oebsRows.count()=${oebsRows.count()}")

        // If we don't have anything to update we can quit early
        if (hmdbRows.isEmpty() && oebsRows.isEmpty()) return

        // For all new or stale HMDB cache-rows, fetch framework agreement start / end and update the cache
        runBlocking {
            val products = runCatching {
                HjelpemiddeldatabaseClient.hentProdukterMedHmsnrs(hmdbRows)
            }.getOrElse { e ->
                logg.error("updateCache: HMDB: Failed to fetch products due to: $e")
                e.printStackTrace()
                return@runBlocking
            }

            using(sessionOf(ds)) { session ->
                products.forEach { product ->
                    val frameworkAgreementStart: LocalDate? = product.rammeavtaleStart?.run { LocalDate.parse(this) }
                    val frameworkAgreementEnd: LocalDate? = product.rammeavtaleSlutt?.run { LocalDate.parse(this) }
                    session.run(
                        queryOf(
                            """
                                UPDATE v1_cache_hmdb
                                SET framework_agreement_start = ?, framework_agreement_end = ?, cached_at = NOW()
                                WHERE hmsnr = ?
                                ;
                            """.trimIndent(),
                            frameworkAgreementStart,
                            frameworkAgreementEnd,
                            product.hmsnr,
                        ).asUpdate
                    )
                    logg.info("DEBUG: updateCache: HMDB: Updated hmsnr=${product.hmsnr}, set framework_agreement_start=$frameworkAgreementStart, framework_agreement_end=$frameworkAgreementEnd")

                    // Remove non-existing products from the v1_cache_hmdb-database (people applied for products that doesnt exist according to HMDB)
                    val productsHmsnrs = products.filter { it.hmsnr != null }.map { it.hmsnr!! }
                    val toRemove = hmdbRows.filter { !productsHmsnrs.contains(it) }
                    if (toRemove.isNotEmpty()) {
                        logg.info("DEBUG: updateCache: HMDB: Removing invalid hmsnrs: ${toRemove.count()}")
                        session.run(
                            queryOf(
                                """
                                    DELETE FROM v1_cache_hmdb WHERE hmsnr IN (${toRemove.joinToString { "'$it'" }});
                                """.trimIndent()
                            ).asExecute
                        )
                    }
                }
            }
        }

        // For all new or stale OEBS cache-rows, fetch titles and type and update the cache
        runBlocking {
            val products = runCatching {
                Oebs.getTitleForHmsNrs(oebsRows)
            }.getOrElse { e ->
                logg.error("updateCache: OEBS: Failed to fetch products due to: $e")
                e.printStackTrace()
                return@runBlocking
            }

            val debugHmsnrs = listOf(
                "654321",
                "563412",
                "123456",
                "123441",
                "696969",
                "123123",
                "151515",
                "454545"
            )
            for (hmsnr in debugHmsnrs) products[hmsnr]?.run {
                logg.info("DEBUG: debugHmsnrs: found hmsnr=$hmsnr product: title=${this.first} type=${this.second}")
            } ?: logg.info("DEBUG: debugHmsnrs: did not find hmsnr=$hmsnr")

            using(sessionOf(ds)) { session ->
                products.forEach { (hmsnr, result) ->
                    session.run(
                        queryOf(
                            """
                                UPDATE v1_cache_oebs
                                SET title = ?, type = ?, cached_at = NOW()
                                WHERE hmsnr = ?
                                ;
                            """.trimIndent(),
                            result.first, // Title
                            result.second, // Type
                            hmsnr,
                        ).asUpdate
                    )
                    logg.info("DEBUG: updateCache: OEBS: Updated hmsnr=$hmsnr, set title=${result.first}, type=${result.second}")

                    // Remove non-existing products/accessories from the v1_cache_oebs-database (people applied for products that doesnt exist according to OEBS)
                    val toRemove = oebsRows.filter { !products.containsKey(it) }
                    if (toRemove.isNotEmpty()) {
                        logg.info("DEBUG: updateCache: OEBS: Removing invalid hmsnrs: ${toRemove.count()}")
                        session.run(
                            queryOf(
                                """
                                    DELETE FROM v1_cache_oebs WHERE hmsnr IN (${toRemove.joinToString { "'$it'" }});
                                """.trimIndent()
                            ).asExecute
                        )
                    }
                }
            }
        }
    }
}
