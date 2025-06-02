package no.nav.hjelpemidler

import mu.KLogger
import mu.KMarkerFactory

// TODO denne funksjonaliteten finnes i nyere versjoner av Hotlibs. Bruk Hotlibs sin implementering når vi har fått oppdatert til nyere versjon.

val teamLogsMarker = KMarkerFactory.getMarker("TEAM_LOGS")
fun KLogger.teamTrace(throwable: Throwable? = null, message: () -> String) = trace(teamLogsMarker, message(), throwable)
fun KLogger.teamDebug(throwable: Throwable? = null, message: () -> String) = debug(teamLogsMarker, message(), throwable)
fun KLogger.teamInfo(throwable: Throwable? = null, message: () -> String) = info(teamLogsMarker, message(), throwable)
fun KLogger.teamWarn(throwable: Throwable? = null, message: () -> String) = warn(teamLogsMarker, message(), throwable)
fun KLogger.teamError(throwable: Throwable? = null, message: () -> String) = error(teamLogsMarker, message(), throwable)
