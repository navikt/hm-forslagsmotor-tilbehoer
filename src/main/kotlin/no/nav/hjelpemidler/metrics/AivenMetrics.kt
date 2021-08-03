package no.nav.hjelpemidler.metrics

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import java.time.Instant

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class AivenMetrics {
    private val influxHost = Configuration.influxDB["INFLUX_HOST"] ?: "http://localhost"
    private val influxPort = Configuration.influxDB["INFLUX_PORT"] ?: "1234"
    private val influxDatabaseName = Configuration.influxDB["INFLUX_DATABASE_NAME"] ?: "defaultdb"
    private val influxUser = Configuration.influxDB["INFLUX_USER"] ?: "user"
    private val influxPassword = Configuration.influxDB["INFLUX_PASSWORD"] ?: "password"

    private val client = InfluxDBClientFactory.createV1(
        "$influxHost:$influxPort",
        influxUser,
        influxPassword.toCharArray(),
        influxDatabaseName,
        null
    )

    fun writeEvent(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) = runBlocking {
        // TODO: Get nanoseconds
        val point = Point(measurement)
            .addTags(DEFAULT_TAGS)
            .addTags(tags)
            .addFields(fields)
            .time(Instant.now().toEpochMilli(), WritePrecision.MS)

        client.writeApi.writePoint(point)
    }

    fun example1() {
        writeEvent(EXAMPLE1, fields = mapOf("counter" to 1L), tags = emptyMap())
    }

    fun initieltDatasettStoerelse(antall: Long) {
        writeEvent(INITIELT_DATASETT_STOERELSE, fields = mapOf("antall" to antall), tags = emptyMap())
    }

    companion object {
        private val DEFAULT_TAGS: Map<String, String> = mapOf(
            "application" to (Configuration.application["NAIS_APP_NAME"] ?: "hm-forslagsmotor-tilbehoer"),
            "cluster" to (Configuration.application["NAIS_CLUSTER_NAME"] ?: "dev-gcp"),
            "namespace" to (Configuration.application["NAIS_NAMESPACE"] ?: "teamdigihot")
        )

        private const val PREFIX = "hm-forslagsmotor-tilbehoer"
        const val EXAMPLE1 = "$PREFIX.EXAMPLE1"
        const val INITIELT_DATASETT_STOERELSE = "$PREFIX.initielt.datasett.stoerelse"
    }
}
