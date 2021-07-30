package no.nav.hjelpemidler.configuration

import no.finn.unleash.strategy.Strategy

internal object ByClusterStrategy : Strategy {
    override fun getName(): String = "byCluster"

    override fun isEnabled(parameters: MutableMap<String, String>?): Boolean {

        val clustersParameter = parameters?.get("cluster") ?: return false
        val alleClustere = clustersParameter.split(",").map { it.trim() }.map { it.toLowerCase() }.toList()

        return alleClustere.contains(Cluster.current.asString())
    }
}
