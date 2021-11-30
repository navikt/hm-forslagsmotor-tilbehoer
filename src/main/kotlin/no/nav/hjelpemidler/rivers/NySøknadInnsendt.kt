package no.nav.hjelpemidler.rivers

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.suggestionengine.Soknad
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NySøknadInnsendt(
    rapidsConnection: RapidsConnection,
    val se: SuggestionEngine
) : PacketListenerWithOnError {
    private val objectMapper = jacksonObjectMapper()

    init {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "nySoknad") }
            validate { it.requireKey("signatur", "soknad") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("onPacket: nySoknad: received (signatur=${packet["signatur"].textValue()})")

        // Parse packet to relevant data
        val rawJson: String = packet["soknad"].toString()
        val soknad = objectMapper.readValue<Soknad>(rawJson)

        if (se.knowsOfSoknadID(soknad.soknad.id)) {
            logg.info("SuggestionEngine already knowns about soknad with id=${soknad.soknad.id}, ignoring..")
            return
        }

        val list = soknad.soknad.hjelpemidler.hjelpemiddelListe.toList()

        val totalAccessoriesInApplication =
            list.map { it.tilbehorListe.map { it.antall }.fold(0) { a, b -> a + b } }.fold(0) { a, b -> a + b }
        val partialOrFullUseOfSuggestionsOrLookup = list.any {
            it.tilbehorListe.any { (it.brukAvForslagsmotoren?.lagtTilFraForslagsmotoren ?: false || it.brukAvForslagsmotoren?.oppslagAvNavn ?: false) }
        }

        // We only record this statistic if there were accessories in the application, as that is always the case if partialOrFullUseOfSuggestionsOrLookup=true,
        // so if we didn't the "GROUP BY partialOrFullUseOfSuggestionsOrLookup" in Grafana would make little sense for comparison.
        if (totalAccessoriesInApplication > 1)
            AivenMetrics().totalAccessoriesInApplication(
                totalAccessoriesInApplication,
                partialOrFullUseOfSuggestionsOrLookup
            )

        AivenMetrics().soknadProcessed(list.size)

        // Create metrics based on data
        for (product in list) {
            if (product.tilbehorListe.isEmpty()) {
                AivenMetrics().productWithoutAccessories()
                continue
            }

            // Record cases where name lookup from oebs was used
            for (accessory in product.tilbehorListe) {
                val bruk = accessory.brukAvForslagsmotoren ?: continue
                if (bruk.oppslagAvNavn) {
                    AivenMetrics().productWithAccessoryManuallyAddedWithAutomaticNameLookup()
                }
            }

            val suggestions = se.allSuggestionsForHmsNr(product.hmsNr)
            if (suggestions.isEmpty()) {
                AivenMetrics().productWithoutSuggestions()
                continue
            }

            // How precise were our suggestions check for each accessory
            for (accessory in product.tilbehorListe) {
                var wasSuggested = -1
                suggestions.forEachIndexed { idx, suggestion ->
                    if (suggestion.hmsNr == accessory.hmsnr) {
                        wasSuggested = idx + 1 // Get 1-indexed list (1, 2, 3, ...)
                    }
                }

                if (wasSuggested >= 0) {
                    val forslagsmotorBrukt = accessory.brukAvForslagsmotoren?.lagtTilFraForslagsmotoren ?: false
                    AivenMetrics().productWasSuggested(wasSuggested, forslagsmotorBrukt)
                } else {
                    AivenMetrics().productWasNotSuggestedAtAll()
                }
            }
        }

        // Learn from data
        se.learnFromSoknad(soknad)
    }
}
