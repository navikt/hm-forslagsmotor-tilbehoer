package no.nav.hjelpemidler.metrics

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.helse.rapids_rivers.MessageContext
import java.time.LocalDateTime
import java.util.UUID

private val mapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

class MetabaseMetricsProducer(
    private val messageContext: MessageContext
) {
    fun hendelseOpprettet(
        measurement: String,
        fields: Map<String, Any>,
        tags: Map<String, String>,
    ) {
        messageContext.publish(
            measurement,
            mapper.writeValueAsString(
                mapOf(
                    "eventId" to UUID.randomUUID(),
                    "eventName" to "hm-bigquery-sink-hendelse",
                    "schemaId" to "hendelse_v2",
                    "payload" to mapOf(
                        "opprettet" to LocalDateTime.now(),
                        "navn" to measurement,
                        "kilde" to "hm-forslagsmotor-tilbehoer",
                        "data" to fields.mapValues { it.value.toString() }
                            .plus(tags)
                            .filterKeys { it != "counter" }
                    )
                )
            )
        )
    }
}
