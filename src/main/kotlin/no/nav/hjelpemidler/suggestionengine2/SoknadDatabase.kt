package no.nav.hjelpemidler.suggestionengine

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.UUID

private val logg = KotlinLogging.logger {}

internal class SoknadDatabase(testing: List<Hjelpemidler>? = null) : Closeable {
    private var store: MutableList<Hjelpemidler> = mutableListOf()

    init {
        if (testing != null) {
            store.addAll(testing)
        }
    }

    override fun close() {
    }

    @Synchronized
    fun has(id: UUID): Boolean {
        return store.any { it.soknad.id == id }
    }

    @Synchronized
    fun add(soknad: Hjelpemidler) {
        if (has(soknad.soknad.id)) throw Exception("søknad already exists in the database")
    }

    @Synchronized
    fun getAccessories(): Map<String, List<Tilbehoer>> {
        return store.map { it.soknad.hjelpemidler.hjelpemiddelListe }
            .fold(mutableListOf<Hjelpemiddel>()) { a, b ->
                a.addAll(b)
                a
            }.groupBy { it.hmsNr }.mapValues {
                it.value.map { it.tilbehorListe }.fold(mutableListOf<Tilbehoer>()) { c, d ->
                    c.addAll(d)
                    c
                }
            }
    }

    @Synchronized
    fun getAccessoriesByProductHmsnr(
        hmsnr: String,
        suggestionsFrom: LocalDateTime = LocalDateTime.of(0, 1, 1, 0, 0)
    ): List<Tilbehoer> {
        return store.filter { it.created.isAfter(suggestionsFrom) }
            .map {
                it.soknad.hjelpemidler.hjelpemiddelListe.filter { it.hmsNr == hmsnr }.map {
                    it.tilbehorListe
                }.fold(mutableListOf<Tilbehoer>()) { a, b ->
                    a.addAll(b)
                    a
                }
            }.fold(mutableListOf<Tilbehoer>()) { a, b ->
                a.addAll(b)
                a
            }
    }
}
