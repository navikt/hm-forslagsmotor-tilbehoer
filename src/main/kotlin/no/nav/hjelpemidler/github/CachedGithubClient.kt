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

    private val BESTILLINGSORDNINGSORTIMENT_CACHE: Cache<String, List<BestillingsHjelpemiddel>> =
        CacheConfig.cacheManager.createCache(
            "bestillingsordningsortiment",
            CacheConfig.oneHour<String, List<BestillingsHjelpemiddel>>()
        )
}
