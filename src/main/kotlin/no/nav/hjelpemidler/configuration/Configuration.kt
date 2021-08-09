package no.nav.hjelpemidler.configuration

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties

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
            "SENSU_URL" to "https://digihot-proxy.prod-fss-pub.nais.io/sensu",
            "unleash.url" to "https://unleash.nais.io/api/",

            "kafka.client.id" to "hm-forslagsmotor-tilbehoer-prod",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",

            "application.http.port" to "8080",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://prod-gcp.teamdigihot.hm-soknadsbehandling-db/.default",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "dev",
            "SENSU_URL" to "https://digihot-proxy.dev-fss-pub.nais.io/sensu",
            "unleash.url" to "https://unleash.nais.io/api/",

            "kafka.client.id" to "hm-forslagsmotor-tilbehoer-dev",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",

            "application.http.port" to "8080",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://dev-gcp.teamdigihot.hm-soknadsbehandling-db/.default",
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "local",
            "SENSU_URL" to "http://localhost:8456/sensu",
            "unleash.url" to "https://unleash.nais.io/api/",

            "kafka.client.id" to "hm-forslagsmotor-tilbehoer-local",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",

            "application.http.port" to "8123", // 8080 is default in R&R

            "kafka.brokers" to "host.docker.internal:9092",

            "KAFKA_KEYSTORE_PATH" to "",
            "KAFKA_TRUSTSTORE_PATH" to "",
            "KAFKA_CREDSTORE_PASSWORD" to "",

            "AZURE_TENANT_BASEURL" to "http://localhost:9099",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "123",
            "AZURE_APP_CLIENT_SECRET" to "dummy",
            "AZURE_AD_SCOPE" to "123",

            "INFLUX_HOST" to "abc",
            "INFLUX_PORT" to "abc",
            "INFLUX_DATABASE_NAME" to "abc",
            "INFLUX_USER" to "abc",
            "INFLUX_PASSWORD" to "abc",

            "TOKEN_X_WELL_KNOWN_URL" to "abc",
            "TOKEN_X_CLIENT_ID" to "abc",
        )
    )

    val aivenConfig: Map<String, String> = mapOf(
        "RAPID_APP_NAME" to "hm-forslagsmotor-tilbehoer",
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.topic", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to "hm-forslagsmotor-tilbehoer-v1",
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
        "AZURE_AD_SCOPE" to config()[Key("AZURE_AD_SCOPE", stringType)],
    )

    val tokenX: Map<String, String> = mapOf(
        "TOKEN_X_CLIENT_ID" to config()[Key("TOKEN_X_CLIENT_ID", stringType)],
        "TOKEN_X_WELL_KNOWN_URL" to config()[Key("TOKEN_X_WELL_KNOWN_URL", stringType)],
    )

    val influxDB: Map<String, String> = mapOf(
        "INFLUX_HOST" to config()[Key("INFLUX_HOST", stringType)],
        "INFLUX_PORT" to config()[Key("INFLUX_PORT", stringType)],
        "INFLUX_DATABASE_NAME" to config()[Key("INFLUX_DATABASE_NAME", stringType)],
        "INFLUX_USER" to config()[Key("INFLUX_USER", stringType)],
        "INFLUX_PASSWORD" to config()[Key("INFLUX_PASSWORD", stringType)],
    )

    val application: Map<String, String> = mapOf(
        "APP_PROFILE" to config()[Key("application.profile", stringType)],
        "SENSU_URL" to config()[Key("SENSU_URL", stringType)],
        "UNLEASH_URL" to config()[Key("unleash.url", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }
}
