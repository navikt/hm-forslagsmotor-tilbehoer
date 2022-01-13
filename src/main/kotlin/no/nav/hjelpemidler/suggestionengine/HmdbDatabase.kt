package no.nav.hjelpemidler.suggestionengine

import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.soknad.db.client.hmdb.HjelpemiddeldatabaseClient
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

internal class HmdbDatabase(testing: Map<String, LocalDate>? = null) : Closeable {
    private val store: MutableMap<String, Storage> = mutableMapOf()
    private var isClosed = false

    init {
        if (testing == null) launchBackgroundRunner()
        else {
            testing.forEach {
                store[it.key] = Storage(it.value, LocalDateTime.now())
            }
        }
    }

    @Synchronized
    override fun close() {
        isClosed = true
    }

    @Synchronized
    fun isClosed(): Boolean {
        return isClosed
    }

    @Synchronized
    fun setFrameworkAgreementStartFor(hmsNr: String, start: LocalDate?) {
        if (start != null) {
            store[hmsNr] = Storage(start, LocalDateTime.now())
        } else {
            store[hmsNr] = Storage()
        }
    }

    @Synchronized
    fun setLastUpdatedFor(hmsNr: String?) {
        store[hmsNr]?.lastUpdated = LocalDateTime.now()
    }

    @Synchronized
    fun removeFrameworkAgreementStartFor(hmsNr: String?) {
        store.remove(hmsNr)
    }

    @Synchronized
    fun getFrameworkAgreementStartFor(hmsNr: String): LocalDate? {
        return store[hmsNr]?.frameworkStart
    }

    @Synchronized
    private fun getAllFrameworkStartTimesWhichHaventBeenFetchedOrNotRefreshedSince(since: LocalDateTime): List<String> {
        return store
            .filterValues { it.lastUpdated == null || it.lastUpdated!!.isBefore(since) }
            .toList().map { it.first }
    }

    private fun launchBackgroundRunner() {
        thread(isDaemon = true) {
            var firstRun = true
            while (true) {
                Thread.sleep(10_000)
                if (isClosed()) return@thread // Exit

                val hmsNrsToCheck = getAllFrameworkStartTimesWhichHaventBeenFetchedOrNotRefreshedSince(
                    LocalDateTime.now().minusHours(24)
                ).toSet()
                if (hmsNrsToCheck.isEmpty()) continue

                logg.info("HMDB database: Running background check for ${hmsNrsToCheck.count()} unknown/outdated framework agreement start dates")

                try {
                    var changes = false

                    val result = runBlocking {
                        HjelpemiddeldatabaseClient.hentProdukterMedHmsnrs(hmsNrsToCheck)
                    }.filter { it.hmsnr != null }.groupBy { it.hmsnr!! }

                    for (hmsNr in result.keys) {
                        val productReferences = result[hmsNr] ?: continue
                        for (product in productReferences) {
                            val start = product.rammeavtaleStart
                            val end = product.rammeavtaleSlutt
                            if (start != null && end != null) {
                                val now = LocalDate.now()
                                val startDate = LocalDate.parse(start)
                                val endDate = LocalDate.parse(end)
                                if (now.isEqual(startDate) || now.isEqual(endDate) || (now.isAfter(startDate) && now.isBefore(endDate))) {
                                    setFrameworkAgreementStartFor(hmsNr, startDate)
                                    changes = true
                                    break // productReferences
                                }
                            }
                        } // productReferences

                        setLastUpdatedFor(hmsNr)
                    }

                    // Remove the ones that doesnt exist?
                    val toRemove = hmsNrsToCheck.filter { !result.containsKey(it) }
                    if (toRemove.isNotEmpty()) logg.info("HMDB database: Removing invalid hmsNrs: ${toRemove.count()}")
                    for (id in toRemove) {
                        removeFrameworkAgreementStartFor(id)
                        changes = true
                    }

                    // Metrics
                    if (firstRun || changes) {
                        firstRun = false
                        AivenMetrics().totalMissingFrameworkAgreementStartDates(getAllFrameworkStartTimesWhichHaventBeenFetchedOrNotRefreshedSince(
                            LocalDateTime.now().minusHours(24)
                        ).count())
                    }

                    logg.info("HMDB database: Done checking on unknown/outdated framework agreement start dates")

                } catch (e: Exception) {
                    logg.warn("failed to fetch framework start dates(for=$hmsNrsToCheck): $e")
                    e.printStackTrace()
                }
            }
        }
    }

    data class Storage(
        var frameworkStart: LocalDate? = null,
        var lastUpdated: LocalDateTime? = null,
    )
}
