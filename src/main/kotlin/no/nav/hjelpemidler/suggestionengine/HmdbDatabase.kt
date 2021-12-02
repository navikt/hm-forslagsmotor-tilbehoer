package no.nav.hjelpemidler.suggestionengine

import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
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
    fun getFrameworkAgreementStartFor(hmsNr: String): LocalDate? {
        return store[hmsNr]?.frameworkStart
    }

    @Synchronized
    private fun getAllFrameworkStartTimesWhichAreUnknownOrNotRefreshedSince(since: LocalDateTime): List<String> {
        return store
            .filterValues { it.frameworkStart == null || (it.lastUpdated != null && it.lastUpdated!!.isBefore(since)) }
            .toList().map { it.first }
    }

    private fun launchBackgroundRunner() {
        thread(isDaemon = true) {
            while (true) {
                Thread.sleep(10_000)
                if (isClosed()) return@thread // Exit

                val hmsNrs = getAllFrameworkStartTimesWhichAreUnknownOrNotRefreshedSince(
                    LocalDateTime.now().minusHours(24)
                ).toSet()

                try {
                    val result = runBlocking {
                        HjelpemiddeldatabaseClient.hentProdukterMedHmsnrs(hmsNrs)
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
                                    setFrameworkAgreementStartFor(product.hmsnr!!, startDate)
                                    break // productReferences
                                }
                            }
                        } // productReferences
                    }
                } catch (e: Exception) {
                    logg.warn("failed to fetch framework start dates(for=$hmsNrs): $e")
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
