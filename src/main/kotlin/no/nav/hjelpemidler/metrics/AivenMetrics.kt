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

    fun soknadHasAccessories(hasAccessories: Boolean) {
        writeEvent(SOKNAD_HAS_ACCESSORIES, fields = mapOf("count" to 1L), tags = mapOf("hasAccessories" to hasAccessories.toString()))
    }

    fun fullUseOfSuggestions() {
        writeEvent(FULL_USE_OF_SUGGESTIONS, fields = mapOf("count" to 1L), tags = emptyMap())
    }

    fun partialUseOfSuggestions() {
        writeEvent(PARTIAL_USE_OF_SUGGESTIONS, fields = mapOf("count" to 1L), tags = emptyMap())
    }

    fun noUseOfSuggestions() {
        writeEvent(NO_USE_OF_SUGGESTIONS, fields = mapOf("count" to 1L), tags = emptyMap())
    }

    fun totalAccessoriesAddedUsingSuggestions(total: Int) {
        writeEvent(TOTAL_ACCESSORIES_ADDED_USING_SUGGESTIONS, fields = mapOf("total" to total), tags = emptyMap())
    }

    fun totaAccessorieslNotAddedUsingSuggestions(total: Int) {
        writeEvent(TOTAL_ACCESSORIES_NOT_ADDED_USING_SUGGESTIONS, fields = mapOf("total" to total), tags = emptyMap())
    }

    fun totalMissingOebsTitles(total: Int) {
        writeEvent(TOTAL_MISSING_OEBS_TITLES, fields = mapOf("total" to total), tags = emptyMap())
    }

    fun totalMissingFrameworkAgreementStartDates(total: Int) {
        writeEvent(TOTAL_MISSING_FRAMEWORK_AGREEMENT_START_DATES, fields = mapOf("total" to total), tags = emptyMap())
    }

    // Old

    fun totalProductsWithAccessorySuggestions(antall: Long) {
        writeEvent(TOTAL_PRODUCTS_WITH_ACCESSORY_SUGGESTIONS, fields = mapOf("antall" to antall), tags = emptyMap())
    }

    fun totalAccessorySuggestions(antall: Long) {
        writeEvent(TOTAL_ACCESSORY_SUGGESTIONS, fields = mapOf("antall" to antall), tags = emptyMap())
    }

    fun totalAccessoriesWithoutADescription(antall: Long) {
        writeEvent(TOTAL_ACCESSORIES_WITHOUT_A_DESCRIPTION, fields = mapOf("antall" to antall), tags = emptyMap())
    }

    fun totalAccessoriesInApplication(tilbehoerISoknadenTotalt: Int, partialOrFullUseOfSuggestionsOrLookup: Boolean) {
        writeEvent(
            TOTAL_ACCESSORIES_IN_APPLICATION,
            fields = mapOf("count" to tilbehoerISoknadenTotalt.toLong()),
            tags = mapOf(
                "partialOrFullUseOfSuggestionsOrLookup" to partialOrFullUseOfSuggestionsOrLookup.toString(),
            )
        )
    }

    fun soknadProcessed(size: Int) {
        writeEvent(SOKNAD_PROCESSED, fields = mapOf("count" to 1L), tags = mapOf("size" to size.toString()))
    }

    fun productWithoutAccessories() {
        writeEvent(PRODUCT_WITHOUT_ACCESSORIES, fields = mapOf("count" to 1L), tags = emptyMap())
    }

    fun productWithoutSuggestions() {
        writeEvent(PRODUCT_WITHOUT_SUGGESTIONS, fields = mapOf("count" to 1L), tags = emptyMap())
    }

    fun productWithAccessoryManuallyAddedWithAutomaticNameLookup() {
        writeEvent(PRODUCT_MANUALLY_ADDED_WITH_AUTO_NAMELOOKUP, fields = mapOf("count" to 1L), tags = emptyMap())
    }

    fun productWasSuggested(wasSuggested: Int, forslagsmotorBrukt: Boolean) {
        writeEvent(
            PRODUCT_WAS_SUGGESTED,
            fields = mapOf("count" to 1L),
            tags = mapOf(
                "index" to wasSuggested.toString(),
                "forslagsmotorBrukt" to forslagsmotorBrukt.toString(),
            )
        )
    }

    fun productWasNotSuggestedAtAll() {
        writeEvent(PRODUCT_WAS_NOT_SUGGESTED_AT_ALL, fields = mapOf("count" to 1L), tags = emptyMap())
    }

    companion object {
        private val DEFAULT_TAGS: Map<String, String> = mapOf(
            "application" to (Configuration.application["NAIS_APP_NAME"] ?: "hm-forslagsmotor-tilbehoer"),
            "cluster" to (Configuration.application["NAIS_CLUSTER_NAME"] ?: "dev-gcp"),
            "namespace" to (Configuration.application["NAIS_NAMESPACE"] ?: "teamdigihot")
        )

        private const val PREFIX = "hm-forslagsmotor-tilbehoer"
        const val EXAMPLE1 = "$PREFIX.EXAMPLE1"
        // const val INITIELT_DATASETT_STOERELSE = "$PREFIX.initielt.datasett.stoerelse"

        const val SOKNAD_HAS_ACCESSORIES = "$PREFIX.soknad.has.accessories"
        const val FULL_USE_OF_SUGGESTIONS = "$PREFIX.full.use.of.suggestions"
        const val PARTIAL_USE_OF_SUGGESTIONS = "$PREFIX.partial.use.of.suggestions"
        const val NO_USE_OF_SUGGESTIONS = "$PREFIX.no.use.of.suggestions"
        const val TOTAL_ACCESSORIES_ADDED_USING_SUGGESTIONS = "$PREFIX.total.accessories.added.using.suggestions"
        const val TOTAL_ACCESSORIES_NOT_ADDED_USING_SUGGESTIONS = "$PREFIX.total.accessories.not.added.using.suggestions"
        const val TOTAL_MISSING_OEBS_TITLES = "$PREFIX.total.missing.oebs.titles"
        const val TOTAL_MISSING_FRAMEWORK_AGREEMENT_START_DATES = "$PREFIX.total.missing.framework.agreement.start.dates"

        const val TOTAL_PRODUCTS_WITH_ACCESSORY_SUGGESTIONS = "$PREFIX.total.products.with.accessory.suggestions"
        const val TOTAL_ACCESSORY_SUGGESTIONS = "$PREFIX.total.accessory.suggestions"
        const val TOTAL_ACCESSORIES_WITHOUT_A_DESCRIPTION = "$PREFIX.total.accessories.without.a.description"
        const val TOTAL_ACCESSORIES_IN_APPLICATION = "$PREFIX.total.accessories.in.application"
        const val SOKNAD_PROCESSED = "$PREFIX.soknad.processed"
        const val PRODUCT_WITHOUT_ACCESSORIES = "$PREFIX.product.without.accessories"
        const val PRODUCT_WITHOUT_SUGGESTIONS = "$PREFIX.product.without.suggestions"
        const val PRODUCT_MANUALLY_ADDED_WITH_AUTO_NAMELOOKUP = "$PREFIX.product.manually.added.with.auto.namelookup"
        const val PRODUCT_WAS_SUGGESTED = "$PREFIX.product.was.suggested"
        const val PRODUCT_WAS_NOT_SUGGESTED_AT_ALL = "$PREFIX.product.was.not.suggested.at.all"
    }
}
