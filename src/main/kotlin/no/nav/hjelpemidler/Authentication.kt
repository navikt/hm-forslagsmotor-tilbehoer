package no.nav.hjelpemidler

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.configuration.Configuration
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector
import java.net.URL
import java.util.concurrent.TimeUnit

private fun httpClientWithProxy() = HttpClient(Apache) {
    install(ContentNegotiation) {
        jackson {
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

fun Application.installAuthentication() {
    // Load tokenx config for rest client authentication
    var tokenxConfig: AuthenticationConfig?
    runBlocking {
        tokenxConfig = AuthenticationConfig(
            metadata = httpClientWithProxy().get(Configuration.tokenX["TOKEN_X_WELL_KNOWN_URL"]!!).body(),
            clientId = Configuration.tokenX["TOKEN_X_CLIENT_ID"]!!,
        )
    }

    val jwkProviderTokenx = JwkProviderBuilder(URL(tokenxConfig!!.metadata.jwksUri))
        // cache up to 10 JWKs for 24 hours
        .cached(10, 24, TimeUnit.HOURS)
        // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    // Load Azure AD config for rest client authentication
    var azureAdConfig: AuthenticationConfig?
    runBlocking {
        azureAdConfig = AuthenticationConfig(
            metadata = httpClientWithProxy().get(Configuration.azureAD["AZURE_APP_WELL_KNOWN_URL"]!!).body(),
            clientId = Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!,
        )
    }

    val jwkProviderAzureAd = JwkProviderBuilder(URL(azureAdConfig!!.metadata.jwksUri))
        // cache up to 10 JWKs for 24 hours
        .cached(10, 24, TimeUnit.HOURS)
        // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("tokenX") {
            verifier(jwkProviderTokenx, tokenxConfig!!.metadata.issuer)
            validate { credentials ->
                try {
                    requireNotNull(credentials.payload.audience) {
                        "Auth: Missing audience in token"
                    }
                    require(credentials.payload.audience.contains(tokenxConfig!!.clientId)) {
                        "Auth: Valid audience not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    null
                }
            }
        }
        jwt("aad") {
            verifier(jwkProviderAzureAd, azureAdConfig!!.metadata.issuer)
            validate { credentials ->
                try {
                    requireNotNull(credentials.payload.audience) {
                        "Auth: Missing audience in token"
                    }
                    require(credentials.payload.audience.contains(azureAdConfig!!.clientId)) {
                        "Auth: Valid audience not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }
}

private data class AuthenticationConfig(
    val metadata: Metadata,
    val clientId: String,
) {
    data class Metadata(
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("jwks_uri") val jwksUri: String,
    )
}
