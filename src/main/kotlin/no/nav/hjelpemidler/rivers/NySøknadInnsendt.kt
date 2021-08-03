package no.nav.hjelpemidler.rivers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.metrics.AivenMetrics
import no.nav.hjelpemidler.suggestionengine.Hjelpemiddel
import no.nav.hjelpemidler.suggestionengine.SuggestionEngine

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class NySøknadInnsendt(
    rapidsConnection: RapidsConnection,
) : PacketListenerWithOnError {
    private val objectMapper = jacksonObjectMapper()

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "nySoknad") }
            validate { it.requireKey("signatur", "soknad") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("onPacket: nySoknad: received (signatur=${packet["signatur"].textValue()})")

        // Parse packet to relevant data
        val rawJson: String = packet["soknad"].toString()
        val list = objectMapper.readValue<Soknad>(rawJson).soknad.hjelpemidler.hjelpemiddelListe.toList()

        AivenMetrics().soknadProcessed(list.size)

        // Create metrics based on data
        for (product in list) {
            if (product.tilbehorListe.isEmpty()) {
                AivenMetrics().productWithoutAccessories()
                continue
            }

            val suggestions = SuggestionEngine.suggestionsForHmsNr(product.hmsNr)
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
                    AivenMetrics().productWasSuggested(wasSuggested)
                } else {
                    AivenMetrics().productWasNotSuggestedAtAll()
                }
            }
        }

        // Learn from data
        SuggestionEngine.learnFromSoknad(list)
    }
}

data class Soknad(
    val soknad: Hjelpemidler,
)

data class Hjelpemidler(
    val hjelpemidler: HjelpemiddelListe,
)

data class HjelpemiddelListe(
    val hjelpemiddelListe: Array<Hjelpemiddel>
)
