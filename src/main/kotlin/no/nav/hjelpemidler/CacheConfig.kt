package no.nav.hjelpemidler

import javax.cache.Cache
import javax.cache.CacheManager
import javax.cache.Caching
import javax.cache.configuration.MutableConfiguration
import javax.cache.expiry.CreatedExpiryPolicy
import javax.cache.expiry.Duration

object CacheConfig {
    val cacheManager: CacheManager = Caching.getCachingProvider().cacheManager
    
    inline fun <reified K, reified V> oneHour() = MutableConfiguration<K, V>()
        .setTypes(K::class.java, V::class.java)
        .setStoreByValue(true)
        .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.ONE_HOUR))
}

inline fun <K, V> withCache(cache: Cache<K, V>, key: K, getValue: (() -> V)): V {
    var value = cache.get(key)
    if (value == null) {
        value = getValue()
        cache.put(key, value)
    }
    return value
}

inline fun <V> withCache(cache: Cache<String, V>, getValue: (() -> V)): V {
    return withCache(cache, "default-key", getValue)
}
