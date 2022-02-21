package no.nav.hjelpemidler.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

internal interface SoknadStore {
    fun add(søknadId: UUID, fnrBruker: String, trygdekontorNr: String, saksblokk: String, saksnr: String): Int
    fun remove(søknadId: UUID)
    fun getPollListSize(): Int?
    fun getOldestInPollList(): LocalDateTime?
    fun getPollingBatch(size: Int): List<Poll>
    fun postPollingUpdate(list: List<Poll>)
}

data class Poll(
    val søknadID: UUID,
    val fnrBruker: String,
    val trygdekontorNr: String,
    val saksblokk: String,
    val saksnr: String,
    val numberOfPollings: Int,
    val lastPolled: LocalDateTime?,
    val created: LocalDateTime?,
)

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {

    override fun add(søknadId: UUID, fnrBruker: String, trygdekontorNr: String, saksblokk: String, saksnr: String): Int =
        time("add") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        """
                            INSERT INTO public.V1_POLL_LIST (
                                SOKNADS_ID,
                                FNR_BRUKER,
                                TKNR,
                                SAKSBLOKK,
                                SAKSNR
                            ) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """.trimIndent().split("\n").joinToString(" "),
                        søknadId,
                        fnrBruker,
                        trygdekontorNr,
                        saksblokk,
                        saksnr,
                    ).asUpdate
                )
            }
        }

    override fun remove(søknadId: UUID) {
        @Language("PostgreSQL") val statement =
            """
            DELETE FROM public.V1_POLL_LIST
            WHERE SOKNADS_ID = ?
            """.trimIndent().split("\n").joinToString(" ")

        time("remove") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        søknadId,
                    ).asExecute
                )
            }
        }
    }

    override fun getPollListSize(): Int? {
        @Language("PostgreSQL") val statement =
            """
            SELECT count(*) AS count FROM public.V1_POLL_LIST
            """.trimIndent().split("\n").joinToString(" ")

        return time("getPollListSize") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                    ).map {
                        it.int("count")
                    }.asSingle
                )
            }
        }
    }

    override fun getOldestInPollList(): LocalDateTime? {
        @Language("PostgreSQL") val statement =
            """
            SELECT MIN(CREATED) as CREATED FROM public.V1_POLL_LIST
            """.trimIndent().split("\n").joinToString(" ")

        return time("getOldestInPollList") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                    ).map {
                        it.localDateTimeOrNull("CREATED")
                    }.asSingle
                )
            }
        }
    }

    override fun getPollingBatch(size: Int): List<Poll> {
        @Language("PostgreSQL") val statement =
            """
            SELECT
                SOKNADS_ID,
                FNR_BRUKER,
                TKNR,
                SAKSBLOKK,
                SAKSNR,
                NUMBER_OF_POLLINGS,
                LAST_POLL,
                CREATED
            FROM public.V1_POLL_LIST
            WHERE (
                LAST_POLL IS NULL
                OR
                LAST_POLL <= NOW() - '60 minutes'::interval
            )
            ORDER BY LAST_POLL ASC NULLS FIRST
            LIMIT $size
            """.trimIndent().split("\n").joinToString(" ")

        return time("getPollingBatch") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                    ).map {
                        Poll(
                            UUID.fromString(it.string("SOKNADS_ID")),
                            it.string("FNR_BRUKER"),
                            it.string("TKNR"),
                            it.string("SAKSBLOKK"),
                            it.string("SAKSNR"),
                            it.int("NUMBER_OF_POLLINGS"),
                            it.localDateTimeOrNull("LAST_POLL"),
                            it.localDateTimeOrNull("CREATED"),
                        )
                    }.asList
                )
            }
        }
    }

    override fun postPollingUpdate(list: List<Poll>) {
        // Update NUMBER_OF_POLLINGS and LAST_POLL columns for the rows we picked out to poll:
        val søknadsIDs: MutableList<String> = mutableListOf()
        for (poll in list) {
            søknadsIDs.add("'${poll.søknadID}'")
        }

        @Language("PostgreSQL") val statement =
            """
            UPDATE public.V1_POLL_LIST
            SET
                NUMBER_OF_POLLINGS = NUMBER_OF_POLLINGS + 1,
                LAST_POLL = now()
            WHERE SOKNADS_ID IN (${søknadsIDs.joinToString(", ")})
            """.trimIndent().split("\n").joinToString(" ")

        val effectedRows = time("postPollingUpdate") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                    ).asUpdate
                )
            }
        }

        // The number of updated rows does not match the number of rows selected for polling
        if (effectedRows != list.size) {
            throw Exception("during attempt to update column LAST_POLL ")
        }
    }

    private inline fun <T : Any?> time(queryName: String, function: () -> T) =
        function()
    /*
    Prometheus.dbTimer.labels(queryName).startTimer().let { timer ->
        function().also {
            timer.observeDuration()
        }
    }*/
}
