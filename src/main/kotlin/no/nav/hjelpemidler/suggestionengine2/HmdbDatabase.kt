package no.nav.hjelpemidler.suggestionengine2

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import java.time.LocalDate
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

internal class HmdbDatabase(testing: Map<String, LocalDate>? = null) : Closeable {
    private val store: MutableMap<String, LocalDate?> = mutableMapOf()
    private var isClosed = false

    init {
        if (testing == null) launchBackgroundRunner()
        else {
            testing.forEach {
                store[it.key] = it.value
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
        store[hmsNr] = start
    }

    @Synchronized
    fun getFrameworkAgreementStartFor(hmsNr: String): LocalDate? {
        return store[hmsNr]
    }

    @Synchronized
    fun getAllUnknownFrameworkStartTimes(): List<String> {
        return store.filterValues { it == null }.toList().map { it.first }
    }

    private fun launchBackgroundRunner() {
        thread(isDaemon = true) {
            while (true) {
                Thread.sleep(10_000)
                if (isClosed()) return@thread // Exit
                // val list = HmdbDatabase.getAllUnknownFrameworkStartTimes()
            }
        }
    }
}
