package no.nav.hjelpemidler.configuration

enum class Cluster {
    DEV_GCP, PROD_GCP;

    companion object {
        val current: Cluster by lazy {
            when (val c = System.getenv("NAIS_CLUSTER_NAME")) {
                "dev-gcp" -> DEV_GCP
                "prod-gcp" -> PROD_GCP
                else -> throw RuntimeException("Ukjent cluster: $c")
            }
        }
    }

    fun asString(): String = name.toLowerCase().replace("_", "-")
}
