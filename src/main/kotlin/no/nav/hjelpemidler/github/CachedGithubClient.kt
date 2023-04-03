package no.nav.hjelpemidler.github

import javax.cache.Cache


class CachedGithubClient(private val githubClient: GithubClient = GithubHttpClient()) : GithubClient {

    private val BESTILLINGSORDNINGSORTIMENT_CACHE: Cache<String, List<BestillingsHjelpemiddel>> =
        CacheConfig.cacheManager.createCache(
            "bestillingsordningsortiment",
            CacheConfig.oneHour<String, List<BestillingsHjelpemiddel>>()
        )

    override fun hentBestillingsordningSortiment(): List<BestillingsHjelpemiddel> {
        return withCache(BESTILLINGSORDNINGSORTIMENT_CACHE) {
            githubClient.hentBestillingsordningSortiment()
        }
    }

    private val RAMMEAVTALER_TILBEHØR_CACHE: Cache<String, Rammeavtaler> =
        CacheConfig.cacheManager.createCache(
            "rammeavtalerTilbehør",
            CacheConfig.oneHour<String, Rammeavtaler>()
        )

    override fun hentRammeavtalerForTilbehør(): Rammeavtaler {
        return withCache(RAMMEAVTALER_TILBEHØR_CACHE) {
            githubClient.hentRammeavtalerForTilbehør()
        }
    }
}
