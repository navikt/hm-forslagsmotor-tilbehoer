package no.nav.hjelpemidler.suggestionengine

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import no.nav.hjelpemidler.oebs.Oebs
import java.time.LocalDateTime
import kotlin.concurrent.thread

private val logg = KotlinLogging.logger {}

internal class OebsDatabase(testing: Map<String, String>? = null, val backgroundRunOnChangeCallback: () -> Unit) :
    Closeable {
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
    fun setTitleFor(hmsNr: String, title: String?) {
        if (title != null) {
            store[hmsNr] = Storage(title, LocalDateTime.now())
        } else {
            store[hmsNr] = Storage() // Request background fetch for hmsNr
        }
    }

    @Synchronized
    fun getTitleFor(hmsNr: String): String? {
        return store[hmsNr]?.title
    }

    @Synchronized
    fun hasTitleReference(hmsNr: String): Boolean {
        return store.contains(hmsNr)
    }

    @Synchronized
    fun removeTitle(hmsNr: String) {
        store.remove(hmsNr)
    }

    @Synchronized
    private fun getAllTitlesWhichAreUnknownOrNotRefreshedSince(since: LocalDateTime): List<String> {
        return store
            .filterValues { it.title == null || (it.lastUpdated != null && it.lastUpdated!!.isBefore(since)) }
            .toList().map { it.first }
    }

    private fun launchBackgroundRunner() {
        thread(isDaemon = true) {
            while (true) {
                Thread.sleep(10_000)
                if (isClosed()) return@thread // Exit

                try {
                    // Get list of hmsnrs to check
                    val hmsNrsToCheck =
                        getAllTitlesWhichAreUnknownOrNotRefreshedSince(LocalDateTime.now().minusHours(24))
                    if (hmsNrsToCheck.isEmpty()) continue

                    logg.info("OEBS database: Running background check for ${hmsNrsToCheck.count()} unknown/outdated titles")

                    // Load titles for all those hmsnrs
                    var changes = false
                    val titles = Oebs.getTitleForHmsNrs(hmsNrsToCheck.toSet())
                    for (title in titles) {
                        // TODO: Mark it as "Del" / non-"Del" (from type field: title.value.second)
                        setTitleFor(title.key, title.value.first)
                        changes = true
                    }

                    // Remove those without titles in titles from hmsNrsToCheck
                    val toRemove = hmsNrsToCheck.filter { !titles.containsKey(it) }
                    if (toRemove.isNotEmpty()) logg.info("OEBS database: Removing invalid hmsNrs: ${toRemove.count()}")
                    for (id in toRemove) {
                        changes = true
                        removeTitle(id)
                    }

                    logg.info("OEBS database: Done checking on unknown/outdated titles")

                    // Notify about changes
                    if (changes)
                        backgroundRunOnChangeCallback()

                } catch (e: Exception) {
                    logg.warn("OEBS database: Background run failed: $e")
                    e.printStackTrace()
                }

                /*var changes = false
                for ((idx, hmsNr) in hmsNrsToCheck.withIndex()) {
                    if (idx % 1000 == 0) logg.info("OEBS database: Running background check: $idx/${hmsNrsToCheck.count()}")
                    try {
                        val titleAndType = Oebs.GetTitleForHmsNr(hmsNr)
                        logg.info("OEBS database: Fetched title for $hmsNr and oebs report it as having type=${titleAndType.second}. New title: \"${titleAndType.first}\"")
                        // TODO: Mark it as "Del" / non-"Del" (from type field: titleAndType.second)
                        setTitleFor(hmsNr, titleAndType.first)
                        changes = true
                    } catch (e: Exception) {
                        // Ignoring non-existing products (statusCode=404), others will be added with
                        // title=noDescription and is thus not returned in suggestion results until the
                        // backgroundRunner retries and fetches the title.
                        if (e.toString().contains("statusCode=404")) {
                            logg.info("OEBS database: Ignoring suggestion with hmsNr=$hmsNr as OEBS returned 404 not found (product doesnt exist): $e")
                            removeTitle(hmsNr) // Do not keep asking for this title
                            continue
                        }
                        logg.warn("OEBS database: Failed to get title for hmsNr=$hmsNr from hm-oebs-api-proxy")
                        e.printStackTrace()
                    }
                }
                if (changes)
                    backgroundRunOnChangeCallback()
                 */
            }
        }
    }

    data class Storage(
        var title: String? = null,
        var lastUpdated: LocalDateTime? = null,
    )
}
