package no.nav.hjelpemidler.metrics

import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import org.influxdb.dto.Point
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class SensuMetrics(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build()
) {
    private val sensuURL = Configuration.application["SENSU_URL"] ?: "http://localhost/unconfigured"
    private val sensuName = "hm-forslagsmotor-tilbehoer-events"

    fun example1() {
        registerPoint(EXMAPLE1, mapOf("counter" to 1L), emptyMap())
    }

    private fun registerPoint(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) {
        logg.debug("Posting point to Influx: measurement {} fields {} tags {} ", measurement, fields, tags)
        val point = Point.measurement(measurement)
            .time(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()), TimeUnit.NANOSECONDS)
            .tag(tags)
            .tag(DEFAULT_TAGS)
            .fields(fields)
            .build()

        try {
            sendEvent(SensuEvent(sensuName, point.lineProtocol()))
        } catch (e: Exception) {
            logg.error("Sending av SensuMetrics-event $sensuName feilet.", e)
        }
    }

    private fun sendEvent(sensuEvent: SensuEvent) {
        val body = HttpRequest.BodyPublishers.ofString(sensuEvent.json)
        val request = HttpRequest.newBuilder()
            .POST(body)
            .uri(URI.create(sensuURL))
            .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", UUID.randomUUID().toString())
            .header("Accepts", "application/json")
            .build()
        val response: HttpResponse<String> = httpClient.send(request, BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            logg.error("sensu metrics unexpected response code from proxy: {}", response.statusCode())
            logg.error("sensu metrics response: {}", response.body().toString())
        }
    }

    private class SensuEvent(sensuName: String, output: String) {
        val json: String = "{" +
            "\"name\":\"" + sensuName + "\"," +
            "\"type\":\"metric\"," +
            "\"handlers\":[\"events_nano\"]," +
            "\"output\":\"" + output.replace("\\", "\\\\", true) + "\"," +
            "\"status\":0" +
            "}"
    }

    companion object {
        private val DEFAULT_TAGS: Map<String, String> = mapOf(
            "application" to (Configuration.application["NAIS_APP_NAME"] ?: "hm-forslagsmotor-tilbehoer"),
            "cluster" to (Configuration.application["NAIS_CLUSTER_NAME"] ?: "dev-fss"),
            "namespace" to (Configuration.application["NAIS_NAMESPACE"] ?: "teamdigihot")
        )

        private const val SOKNADER = "hm-forslagsmotor-tilbehoer"
        const val EVENT = "$SOKNADER.event"
        const val EXMAPLE1 = "$EVENT.example1"
    }
}
