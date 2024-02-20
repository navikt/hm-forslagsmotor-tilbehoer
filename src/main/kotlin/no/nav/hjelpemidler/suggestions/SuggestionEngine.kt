package no.nav.hjelpemidler.suggestions

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.hjelpemidler.client.hmdb.HjelpemiddeldatabaseClient
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.model.CachedTitleAndType
import no.nav.hjelpemidler.model.Hjelpemiddel
import no.nav.hjelpemidler.model.ProductFrontendFiltered
import no.nav.hjelpemidler.model.Soknad
import no.nav.hjelpemidler.model.Suggestion
import no.nav.hjelpemidler.model.Suggestions
import no.nav.hjelpemidler.model.Tilbehoer
import no.nav.hjelpemidler.oebs.Oebs
import org.postgresql.util.PGobject
import java.io.Closeable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

private val logg = KotlinLogging.logger {}

private val MIN_OCCURANCES = 4
private val MAX_NUMBER_OR_RESULTS = 20

interface SuggestionEngine {
    fun suggestions(hmsnr: String): Suggestions
    fun allSuggestionsForHmsnr(hmsnr: String): Suggestions
    fun introspect(): List<ProductFrontendFiltered>
    fun processApplications(soknader: List<Soknad>)
    fun cachedTitleAndTypeFor(hmsnr: String): CachedTitleAndType?
    fun knowsOfSoknadID(soknadsID: UUID): Boolean

    fun testInjectCacheHmdb(hmsnr: String, frameworkAgreementStart: LocalDate?, frameworkAgreementEnd: LocalDate?)
    fun testInjectCacheOebs(hmsnr: String, title: String?, type: String?)
    fun deleteSuggestions(tilbehoerHmsnrTilSletting: List<String>)
}

internal class SuggestionEnginePostgres(
    private val ds: DataSource,
    private val aivenMetrics: AivenMetrics,
    private val hjelpemiddeldatabaseClient: HjelpemiddeldatabaseClient,
    private val oebs: Oebs,
) : SuggestionEngine, Closeable {
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

    override fun suggestions(hmsnr: String): Suggestions {
        var startDate: LocalDate? = null
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    querySuggestions,
                    hmsnr,
                    MIN_OCCURANCES,
                    MAX_NUMBER_OR_RESULTS,
                ).map {
                    it.localDateOrNull("framework_agreement_start")?.run { startDate = this }
                    Suggestion(
                        hmsNr = it.string("hmsnr_tilbehoer"),
                        title = it.string("title"),
                        occurancesInSoknader = it.int("occurances"),
                    )
                }.asList
            )
        }.run {
            Suggestions(
                dataStartDate = startDate,
                suggestions = this,
            )
        }
    }

    override fun allSuggestionsForHmsnr(hmsnr: String): Suggestions {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    queryAllSuggestionsForStatsBuilding,
                    hmsnr,
                ).map {
                    Suggestion(
                        hmsNr = it.string("hmsnr_tilbehoer"),
                        title = null,
                        occurancesInSoknader = it.int("occurances"),
                    )
                }.asList
            )
        }.run {
            Suggestions(
                dataStartDate = null,
                suggestions = this,
            )
        }
    }

    override fun introspect(): List<ProductFrontendFiltered> =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    queryIntrospectAllSuggestions,
                    MIN_OCCURANCES,
                ).map {
                    Pair(
                        it.string("hmsnr_hjelpemiddel"),
                        Triple(
                            it.stringOrNull("title_hjelpemiddel"),
                            it.localDateOrNull("framework_agreement_start"),
                            Suggestion(
                                hmsNr = it.string("hmsnr_tilbehoer"),
                                title = it.string("title"),
                                occurancesInSoknader = it.int("occurances"),
                            ),
                        ),
                    )
                }.asList
            )
        }.groupBy { it.first }.map {
            ProductFrontendFiltered(
                hmsnr = it.key,
                title = it.value.firstOrNull()?.second?.first ?: "",
                suggestions = it.value.fold(mutableListOf<Suggestion>()) { a, b ->
                    a.add(b.second.third)
                    a
                }.take(MAX_NUMBER_OR_RESULTS),
                frameworkAgreementStartDate = it.value.firstOrNull()?.second?.second,
            )
        }.sortedBy { it.hmsnr }

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

        thread(isDaemon = true) {
            runCatching {
                generateStats()
            }.getOrElse { e ->
                logg.error("Failed to generate stats after processing applications: $e")
                e.printStackTrace()
            }
        }
    }

    override fun cachedTitleAndTypeFor(hmsnr: String): CachedTitleAndType? =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        SELECT title, type
                        FROM v1_cache_oebs
                        WHERE hmsnr = ?
                        ;
                    """.trimIndent(),
                    hmsnr,
                ).map {
                    CachedTitleAndType(
                        title = it.stringOrNull("title"),
                        type = it.stringOrNull("type"),
                    )
                }.asSingle
            )
        }

    override fun knowsOfSoknadID(soknadsID: UUID): Boolean =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        SELECT 1
                        FROM v1_soknad
                        WHERE soknads_id = ?
                        ;
                    """.trimIndent(),
                    soknadsID,
                ).map {
                    true
                }.asSingle
            )
        } ?: false

    override fun testInjectCacheHmdb(
        hmsnr: String,
        frameworkAgreementStart: LocalDate?,
        frameworkAgreementEnd: LocalDate?
    ) {
        using(sessionOf(ds)) { session ->
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
                    hmsnr,
                ).asExecute
            )
            session.run(
                queryOf(
                    """
                        INSERT INTO v1_cache_hmdb (hmsnr, framework_agreement_start, framework_agreement_end, cached_at)
                        VALUES (?, ?, ?, NOW())
                        ON CONFLICT DO NOTHING
                        ;
                    """.trimIndent(),
                    hmsnr,
                    frameworkAgreementStart,
                    frameworkAgreementEnd,
                ).asExecute
            )
        }
    }

    override fun testInjectCacheOebs(hmsnr: String, title: String?, type: String?) {
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        UPDATE v1_cache_oebs
                        SET title = ?, type = ?, cached_at = NOW()
                        WHERE hmsnr = ?
                        ;
                    """.trimIndent(),
                    title,
                    type,
                    hmsnr,
                ).asExecute
            )
            session.run(
                queryOf(
                    """
                        INSERT INTO v1_cache_oebs (hmsnr, title, type, cached_at)
                        VALUES (?, ?, ?, NOW())
                        ON CONFLICT DO NOTHING
                        ;
                    """.trimIndent(),
                    hmsnr,
                    title,
                    type,
                ).asExecute
            )
        }
    }

    override fun deleteSuggestions(tilbehoerHmsnrTilSletting: List<String>) =
        using(sessionOf(ds)) { session ->
            tilbehoerHmsnrTilSletting.forEach {
                session.run(
                    queryOf("delete from v1_score_card where hmsnr_tilbehoer = ?", it).asUpdate
                )
            }
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
            logg.info(
                "SoknadStore: waiting until ${
                LocalDateTime.now().plusSeconds(startupRandomDelaySeconds.toLong())
                } before we start updating caches every 60 minutes"
            )
            Thread.sleep((1_000 * startupRandomDelaySeconds).toLong())

            var standardInterval = 60 * 60
            // if (Configuration.application["APP_PROFILE"]!! == "dev") standardInterval = 60

            var firstRun = true
            while (true) {
                if (!firstRun) {
                    logg.info(
                        "Background runner sleeping until: ${
                        LocalDateTime.now().plusSeconds(standardInterval.toLong())
                        }"
                    )
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
                    for ((aid, _) in p.tilbehorListe) logg.info("DEBUG: processApplication: accessory: $aid")
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
                                INSERT INTO v1_cache_hmdb (hmsnr)
                                VALUES (?)
                                ON CONFLICT DO NOTHING
                                ;
                            """.trimIndent(),
                            hmsnr,
                        ).asUpdate
                    )
                    if (rowsEffected > 0) {
                        // Make a note of any rows added to start a fetch from HMDB after this.
                        newHmdbRows.add(hmsnr)
                    }
                }

                // Add products and accessories applied for to v1_cache_oebs if does not already exist
                for ((product_hmsnr, product) in productsAppliedForWithAccessories) {
                    logg.info("DEBUG: processApplication: inserting v1_cache_oebs row: pid=$product_hmsnr title=null type=null")
                    rowsEffected = transaction.run(
                        queryOf(
                            """
                                INSERT INTO v1_cache_oebs (hmsnr)
                                VALUES (?)
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
                        logg.info("DEBUG: processApplication: inserting v1_cache_oebs row: aid=${accessory.hmsnr} title=null type=null")
                        rowsEffected = transaction.run(
                            queryOf(
                                """
                                INSERT INTO v1_cache_oebs (hmsnr)
                                VALUES (?)
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
                            SELECT hmsnr
                            FROM v1_cache_hmdb
                            WHERE
                                -- If the initial prefetch was not completed we pick them up the next time the background
                                -- runner updates the cache
                                cached_at IS NULL

                                -- or else lets check every 30 days in case something has changed.
                                OR cached_at < NOW() - interval '30 days'
                            ;
                        """.trimIndent()
                    ).map {
                        it.string("hmsnr")
                    }.asList
                )
            }.chunked(100).firstOrNull()?.toSet()
                ?: listOf<String>().toSet() // Chunked: we do a maximum of 100 rows per call so that they spread over time
        } else {
            newHmdbRows?.toSet() ?: listOf<String>().toSet()
        }

        val oebsRows = if (newHmdbRows == null && newOebsRows == null) {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        """
                            SELECT hmsnr
                            FROM v1_cache_oebs
                            WHERE
                                -- If the initial prefetch was not completed we pick them up the next time the background
                                -- runner updates the cache
                                cached_at IS NULL

                                -- or else lets check every 30 days in case something has changed.
                                OR cached_at < NOW() - interval '30 days'
                            ;
                        """.trimIndent()
                    ).map {
                        it.string("hmsnr")
                    }.asList
                )
            }.chunked(100).firstOrNull()?.toSet()
                ?: listOf<String>().toSet() // Chunked: we do a maximum of 100 rows per call so that they spread over time
        } else {
            newOebsRows?.toSet() ?: listOf<String>().toSet()
        }

        logg.info("DEBUG: updateCache: newRows=${newHmdbRows != null && newOebsRows != null} hmdbRows.count()=${hmdbRows.count()} oebsRows.count()=${oebsRows.count()}")

        // For all new or stale HMDB cache-rows, fetch framework agreement start / end and update the cache
        runBlocking {
            if (hmdbRows.isEmpty()) return@runBlocking

            if (Configuration.application["APP_PROFILE"]!! == "local") {
                logg.info("Hmdb not available in the local-environment")
                return@runBlocking
            }

            val products = runCatching {
                hjelpemiddeldatabaseClient.hentProdukter(hmdbRows)
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
                }

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

        // For all new or stale OEBS cache-rows, fetch titles and type and update the cache
        runBlocking {
            if (oebsRows.isEmpty()) return@runBlocking

            if (Configuration.application["APP_PROFILE"]!! == "local") {
                logg.info("Oebs not available in the local-environment")
                return@runBlocking
            }

            val products = runCatching {
                oebs.getTitleForHmsNrs(oebsRows)
            }.getOrElse { e ->
                logg.error("updateCache: OEBS: Failed to fetch products due to: $e")
                e.printStackTrace()
                return@runBlocking
            }

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
                }

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

        // Lets regenerate stats regarding missing framework agreement start-dates and titles
        using(sessionOf(ds)) { session ->
            var totalMissingFrameworkAgreementStartDates = -1
            var totalMissingOebsTitles = -1

            val timeElapsed = measureTimeMillis {
                totalMissingFrameworkAgreementStartDates = session.run(
                    queryOf(
                        """
                        SELECT count(hmsnr) AS c
                        FROM v1_cache_hmdb
                        WHERE cached_at IS NULL
                        """.trimIndent(),
                    ).map {
                        it.int("c")
                    }.asSingle
                ) ?: 0

                totalMissingOebsTitles = session.run(
                    queryOf(
                        """
                        SELECT count(hmsnr) AS c
                        FROM v1_cache_oebs
                        WHERE cached_at IS NULL
                        """.trimIndent(),
                    ).map {
                        it.int("c")
                    }.asSingle
                ) ?: 0
            }

            logg.info("Suggestion engine stats calculated (totalMissingFrameworkAgreementStartDates=$totalMissingFrameworkAgreementStartDates, totalMissingOebsTitles=$totalMissingOebsTitles, timeElapsed=${timeElapsed}ms)")

            if (Configuration.application["APP_PROFILE"]!! != "local") {
                aivenMetrics.totalMissingFrameworkAgreementStartDates(totalMissingFrameworkAgreementStartDates)
                aivenMetrics.totalMissingOebsTitles(totalMissingOebsTitles)
            }
        }
    }

    private fun generateStats() {
        using(sessionOf(ds)) { session ->
            var totalProductsWithAccessorySuggestions = -1
            var totalAccessorySuggestions = -1
            var totalAccessoriesWithoutADescription = -1

            val timeElapsed = measureTimeMillis {
                // Fetch all suggestions
                val allSuggestions = session.run(
                    queryOf(
                        queryNumberOfSuggestionsForAllProducts,
                    ).map {
                        it.int("suggestions")
                    }.asList
                )

                totalProductsWithAccessorySuggestions = allSuggestions.count()
                totalAccessorySuggestions = allSuggestions.sum()

                // Fetch all suggestions filtered by "title IS NULL"
                totalAccessoriesWithoutADescription = session.run(
                    queryOf(
                        queryNumberOfSuggestionsWithNoTitleYetForAllProducts,
                        MIN_OCCURANCES,
                    ).map {
                        it.int("suggestions")
                    }.asList
                ).sum()
            }

            // Report what we found to influxdb / grafana
            logg.info("Suggestion engine stats calculated (totalProductsWithAccessorySuggestions=$totalProductsWithAccessorySuggestions, totalAccessorySuggestions=$totalAccessorySuggestions, totalAccessoriesWithoutADescription=$totalAccessoriesWithoutADescription, timeElapsed=${timeElapsed}ms)")

            if (Configuration.application["APP_PROFILE"]!! != "local") {
                aivenMetrics.totalProductsWithAccessorySuggestions(totalProductsWithAccessorySuggestions.toLong())
                aivenMetrics.totalAccessorySuggestions(totalAccessorySuggestions.toLong())
                aivenMetrics.totalAccessoriesWithoutADescription(totalAccessoriesWithoutADescription.toLong())
            }
        }
    }
}
