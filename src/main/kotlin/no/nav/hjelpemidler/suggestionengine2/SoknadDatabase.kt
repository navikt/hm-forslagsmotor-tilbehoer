package no.nav.hjelpemidler.suggestionengine

import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.UUID

private val logg = KotlinLogging.logger {}

object SoknadDatabase {
    var store: MutableList<Hjelpemidler> = mutableListOf()

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
    fun getAccessoriesByProductHmsnr(hmsnr: String, suggestionsFrom: LocalDateTime = LocalDateTime.of(0, 0, 0, 0, 0)): List<Tilbehoer> {
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
