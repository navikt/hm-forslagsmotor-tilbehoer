package no.nav.hjelpemidler.suggestionengine

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

internal class OebsDatabase(testing: Map<String, String>? = null) : Closeable {
    private val store: MutableMap<String, String?> = mutableMapOf()
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
    fun setTitleFor(hmsNr: String, title: String?) {
        store[hmsNr] = title
    }

    @Synchronized
    fun getTitleFor(hmsNr: String): String? {
        return store[hmsNr]
    }

    @Synchronized
    fun getAllUnknownTitles(): List<String> {
        return store.filterValues { it == null }.toList().map { it.first }
    }

    private fun launchBackgroundRunner() {
        thread(isDaemon = true) {
            while (true) {
                Thread.sleep(10_000)
                if (isClosed()) return@thread // Exit
                // val list = OebsDatabase.getAllUnknownTitles()
            }
        }
    }
}
