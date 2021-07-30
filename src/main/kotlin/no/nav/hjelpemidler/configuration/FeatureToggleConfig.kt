package no.nav.hjelpemidler.configuration

import no.finn.unleash.DefaultUnleash
import no.finn.unleash.Unleash
import no.finn.unleash.util.UnleashConfig

internal object FeatureToggleConfig {

    private val APP_NAME = "hm-forslagsmotor-tilbehoer"

    var config: UnleashConfig = UnleashConfig.builder()
        .appName(APP_NAME)
        .instanceId("$APP_NAME-${Configuration.application["APP_PROFILE"]}")
        .unleashAPI(Configuration.application["UNLEASH_URL"])
        .build()

    var unleash: Unleash = DefaultUnleash(config, ByClusterStrategy)
}
