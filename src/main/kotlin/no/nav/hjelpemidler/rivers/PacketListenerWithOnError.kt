package no.nav.hjelpemidler.rivers

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.teamInfo

private val logg = KotlinLogging.logger {}

class RiverRequiredKeyMissingException(msg: String) : Exception(msg)

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.teamInfo { "River required keys had problems in parsing message from rapid: ${problems.toExtendedReport()}" }
        throw RiverRequiredKeyMissingException("River required keys had problems in parsing message from rapid, see Team Logs for details")
    }
}
