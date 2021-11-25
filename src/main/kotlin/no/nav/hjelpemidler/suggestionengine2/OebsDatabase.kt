package no.nav.hjelpemidler.suggestionengine2

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import no.nav.hjelpemidler.oebs.Oebs
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

internal class OebsDatabase(testing: Map<String, String>? = null, val generateStats: () -> Unit) : Closeable {
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
    fun removeTitle(hmsNr: String) {
        store.remove(hmsNr)
    }

    @Synchronized
    private fun getAllUnknownTitles(): List<String> {
        return store.filterValues { it == null }.toList().map { it.first }
    }

    private fun launchBackgroundRunner() {
        thread(isDaemon = true) {
            while (true) {
                Thread.sleep(10_000)
                if (isClosed()) return@thread // Exit

                var changes = false
                for (hmsNr in getAllUnknownTitles()) {
                    try {
                        val titleAndType = Oebs.GetTitleForHmsNr(hmsNr)
                        logg.info("DEBUG: Fetched title for $hmsNr and oebs report it as having type: ${titleAndType.second}. Title: ${titleAndType.first}")
                        // TODO: Mark it as Del / non-Del (from type field: titleAndType.second)
                        setTitleFor(hmsNr, titleAndType.first)
                        changes = true
                    } catch (e: Exception) {
                        // Ignoring non-existing products (statusCode=404), others will be added with
                        // title=noDescription and is thus not returned in suggestion results until the
                        // backgroundRunner retries and fetches the title.
                        if (e.toString().contains("statusCode=404")) {
                            logg.info("Ignoring suggestion with hmsNr=$hmsNr as OEBS returned 404 not found (product doesnt exist): $e")
                            removeTitle(hmsNr) // Do not keep asking for this title
                            continue
                        }
                        logg.warn("failed to get title for hmsNr from hm-oebs-api-proxy")
                        e.printStackTrace()
                    }
                }
                if (changes) generateStats()
            }
        }
    }
}
