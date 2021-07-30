package no.nav.hjelpemidler

import org.apache.kafka.common.KafkaException
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class SomeTest {
    @Test
    fun `Run some test`() {
        assertFailsWith<KafkaException> {
            // Something that may throw
            throw KafkaException("hello")
        }
    }
}
