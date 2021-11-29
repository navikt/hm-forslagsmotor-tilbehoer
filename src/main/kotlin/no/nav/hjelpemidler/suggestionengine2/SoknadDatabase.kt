package no.nav.hjelpemidler.suggestionengine2

import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import java.time.LocalDate
import java.util.UUID

private val logg = KotlinLogging.logger {}

internal class SoknadDatabase(testing: List<Soknad>? = null) : Closeable {
    private var store: MutableList<Soknad> = mutableListOf()

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
    fun add(soknad: Soknad) {
        if (has(soknad.soknad.id)) throw Exception("søknad already exists in the database")
        store.add(soknad)
    }

    @Synchronized
    fun getAllKnownProductHmsnrs(): Set<String> {
        // Transform list of soknader into a list of unique hmsNrs
        return store.map { it.soknad.hjelpemidler.hjelpemiddelListe.map { it.hmsNr } }
            .fold(mutableListOf<String>()) { a, b ->
                a.addAll(b)
                a
            }.toSet()
    }

    @Synchronized
    fun getAccessoriesByProductHmsnr(
        hmsnr: String,
        suggestionsFrom: LocalDate = LocalDate.of(0, 1, 1)
    ): List<Tilbehoer> {
        return store.filter { it.created.isAfter(suggestionsFrom.atStartOfDay()) }
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
            }.filter { it.hmsnr != "000000" }
    }
}
