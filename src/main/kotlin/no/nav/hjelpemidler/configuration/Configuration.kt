package no.nav.hjelpemidler.configuration

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

internal object Configuration {

    private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
        "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
        else -> {
            systemProperties() overriding EnvironmentVariables overriding localProperties
        }
    }

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "prod",
            "OEBS_API_PROXY_URL" to "hm-oebs-api-proxy.prod-fss-pub.nais.io",

            "kafka.client.id" to "hm-forslagsmotor-tilbehoer-prod",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",
            "kafka.consumer.id" to "hm-forslagsmotor-tilbehoer-v1",

            "application.http.port" to "8080",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB" to "api://prod-gcp.teamdigihot.hm-soknadsbehandling-db/.default",
            "AZURE_AD_SCOPE_OEBSAPIPROXY" to "api://prod-fss.teamdigihot.hm-oebs-api-proxy/.default",

            "GRUNNDATA_SEARCH_URL" to "http://hm-grunndata-search",
            "GITHUB_TILBEHOR_LISTE" to "https://navikt.github.io/hm-utils/tilbehor_2_prod.json",
            "GITHUB_RESERVEDELER_LISTE" to "https://navikt.github.io/hm-utils/reservedeler_2_prod.json",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "dev",
            "OEBS_API_PROXY_URL" to "hm-oebs-api-proxy.dev-fss-pub.nais.io",

            "kafka.client.id" to "hm-forslagsmotor-tilbehoer-dev",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",
            "kafka.consumer.id" to "hm-forslagsmotor-tilbehoer-v2",

            "application.http.port" to "8080",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB" to "api://dev-gcp.teamdigihot.hm-soknadsbehandling-db/.default",
            "AZURE_AD_SCOPE_OEBSAPIPROXY" to "api://dev-fss.teamdigihot.hm-oebs-api-proxy/.default",

            "GRUNNDATA_SEARCH_URL" to "http://hm-grunndata-search",
            "GITHUB_TILBEHOR_LISTE" to "https://navikt.github.io/hm-utils/tilbehor_2_dev.json",
            "GITHUB_RESERVEDELER_LISTE" to "https://navikt.github.io/hm-utils/reservedeler_2_dev.json",
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "local",
            "OEBS_API_PROXY_URL" to "http://localhost:8456/oebs-api-proxy",

            "kafka.client.id" to "hm-forslagsmotor-tilbehoer-local",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",
            "kafka.consumer.id" to "hm-forslagsmotor-tilbehoer-v1",

            "application.http.port" to "8123", // 8080 is default in R&R

            "kafka.brokers" to "host.docker.internal:9092",

            "KAFKA_KEYSTORE_PATH" to "",
            "KAFKA_TRUSTSTORE_PATH" to "",
            "KAFKA_CREDSTORE_PASSWORD" to "",

            "AZURE_TENANT_BASEURL" to "http://localhost:9099",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "123",
            "AZURE_APP_CLIENT_SECRET" to "dummy",
            "AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB" to "123",
            "AZURE_AD_SCOPE_OEBSAPIPROXY" to "123",
            "AZURE_APP_WELL_KNOWN_URL" to "123",

            "TOKEN_X_WELL_KNOWN_URL" to "abc",
            "TOKEN_X_CLIENT_ID" to "abc",

            "DB_HOST" to "host.docker.internal",
            "DB_PORT" to "5434",
            "DB_DATABASE" to "hm-forslagsmotor-tilbehoer",
            "DB_USERNAME" to "",
            "DB_PASSWORD" to "",

            "GRUNNDATA_SEARCH_URL" to "http://host.docker.internal",
            "GITHUB_TILBEHOR_LISTE" to "https://navikt.github.io/hm-utils/tilbehor_2_dev.json",
            "GITHUB_RESERVEDELER_LISTE" to "https://navikt.github.io/hm-utils/reservedeler_2_dev.json",
        )
    )

    val aivenConfig: Map<String, String> = mapOf(
        "RAPID_APP_NAME" to "hm-forslagsmotor-tilbehoer",
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.topic", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to config()[Key("kafka.consumer.id", stringType)],
        "KAFKA_BROKERS" to config()[Key("kafka.brokers", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("kafka.reset.policy", stringType)],
        "KAFKA_TRUSTSTORE_PATH" to config()[Key("KAFKA_TRUSTSTORE_PATH", stringType)],
        "KAFKA_KEYSTORE_PATH" to config()[Key("KAFKA_KEYSTORE_PATH", stringType)],
        "KAFKA_CREDSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "HTTP_PORT" to config()[Key("application.http.port", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    val azureAD: Map<String, String> = mapOf(
        "AZURE_TENANT_BASEURL" to config()[Key("AZURE_TENANT_BASEURL", stringType)],
        "AZURE_APP_TENANT_ID" to config()[Key("AZURE_APP_TENANT_ID", stringType)],
        "AZURE_APP_CLIENT_ID" to config()[Key("AZURE_APP_CLIENT_ID", stringType)],
        "AZURE_APP_CLIENT_SECRET" to config()[Key("AZURE_APP_CLIENT_SECRET", stringType)],

        "AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB" to config()[Key("AZURE_AD_SCOPE_SOKNADSBEHANDLINGDB", stringType)],
        "AZURE_AD_SCOPE_OEBSAPIPROXY" to config()[Key("AZURE_AD_SCOPE_OEBSAPIPROXY", stringType)],

        "AZURE_APP_WELL_KNOWN_URL" to config()[Key("AZURE_APP_WELL_KNOWN_URL", stringType)],
    )

    val tokenX: Map<String, String> = mapOf(
        "TOKEN_X_CLIENT_ID" to config()[Key("TOKEN_X_CLIENT_ID", stringType)],
        "TOKEN_X_WELL_KNOWN_URL" to config()[Key("TOKEN_X_WELL_KNOWN_URL", stringType)],
    )

    val db: Map<String, String> = mapOf(
        "DB_HOST" to config()[Key("DB_HOST", stringType)],
        "DB_PORT" to config()[Key("DB_PORT", stringType)],
        "DB_DATABASE" to config()[Key("DB_DATABASE", stringType)],
        "DB_USERNAME" to config()[Key("DB_USERNAME", stringType)],
        "DB_PASSWORD" to config()[Key("DB_PASSWORD", stringType)],
    )

    val application: Map<String, String> = mapOf(
        "APP_PROFILE" to config()[Key("application.profile", stringType)],
        "OEBS_API_PROXY_URL" to config()[Key("OEBS_API_PROXY_URL", stringType)],
        "GRUNNDATA_SEARCH_URL" to config()[Key("GRUNNDATA_SEARCH_URL", stringType)],
        "GITHUB_TILBEHOR_LISTE" to config()[Key("GITHUB_TILBEHOR_LISTE", stringType)],
        "GITHUB_RESERVEDELER_LISTE" to config()[Key("GITHUB_RESERVEDELER_LISTE", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }
}
