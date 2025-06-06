package no.nav.hjelpemidler.github

import no.nav.hjelpemidler.CacheConfig
import no.nav.hjelpemidler.withCache
import javax.cache.Cache

class CachedGithubClient(private val githubClient: GithubClient = GithubHttpClient()) : GithubClient {

    override fun hentBestillingsordningSortiment(): List<BestillingsHjelpemiddel> {
        return withCache(BESTILLINGSORDNINGSORTIMENT_CACHE) {
            githubClient.hentBestillingsordningSortiment()
        }
    }

    override fun hentTilbehørslister(): Delelister {
        return withCache(TILBEHØRLISTER_CACHE) {
            githubClient.hentTilbehørslister()
        }
    }

    override fun hentReservedelslister(): Delelister {
        return withCache(RESERVEDELSLISTER_CACHE) {
            githubClient.hentReservedelslister()
        }
    }

    fun tilbehørPåRammeavtale(): Set<Hmsnr> {
        return withCache(TILBEHØR_PÅ_RAMMEAVTALE_CACHE) {
            this.hentTilbehørslister().flatMap { it.value.flatMap { it.value } }.toHashSet()
        }
    }

    private val BESTILLINGSORDNINGSORTIMENT_CACHE: Cache<String, List<BestillingsHjelpemiddel>> =
        CacheConfig.cacheManager.createCache(
            "bestillingsordningsortiment",
            CacheConfig.oneHour<String, List<BestillingsHjelpemiddel>>()
        )

    private val TILBEHØRLISTER_CACHE: Cache<String, Delelister> =
        CacheConfig.cacheManager.createCache(
            "tilbehørslister",
            CacheConfig.oneHour<String, Delelister>()
        )

    private val RESERVEDELSLISTER_CACHE: Cache<String, Delelister> =
        CacheConfig.cacheManager.createCache(
            "reservedelslister",
            CacheConfig.oneHour<String, Delelister>()
        )

    private val TILBEHØR_PÅ_RAMMEAVTALE_CACHE: Cache<String, Set<Hmsnr>> =
        CacheConfig.cacheManager.createCache(
            "tilbehørPåRammeavtale",
            CacheConfig.oneHour<String, Set<Hmsnr>>()
        )
}
