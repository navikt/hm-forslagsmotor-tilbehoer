package no.nav.hjelpemidler.client.hmdb

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.service.hmdb.HentProdukter
import no.nav.hjelpemidler.service.hmdb.hentprodukter.Produkt
import java.net.URL

object HjelpemiddeldatabaseClient {
    private val logg = KotlinLogging.logger {}
    private val client =
        GraphQLKtorClient(
            url = URL("${Configuration.application["GRUNNDATA_API_URL"]!!}/graphql"),
            httpClient = HttpClient(engineFactory = Apache),
            serializer = GraphQLClientJacksonSerializer()
        )

    suspend fun hentProdukter(hmsnr: String): List<Produkt> =
        hentProdukter(setOf(hmsnr))

    suspend fun hentProdukter(hmsnr: Set<String>): List<Produkt> {
        if (hmsnr.isEmpty()) return emptyList()
        val request = HentProdukter(variables = HentProdukter.Variables(hmsnr = hmsnr.toList()))
        return try {
            val response = client.execute(request)
            when {
                response.errors != null -> {
                    throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnr=$hmsnr, errors=${response.errors?.map { it.message }}")
                }

                response.data != null -> response.data?.produkter ?: emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            throw RuntimeException("Nettverksfeil under henting av data fra hjelpemiddeldatabasen, hmsnr=$hmsnr", e)
        }
    }
}
