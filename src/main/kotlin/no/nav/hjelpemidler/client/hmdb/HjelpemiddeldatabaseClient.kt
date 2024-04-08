package no.nav.hjelpemidler.client.hmdb

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.netty.handler.codec.http.HttpHeaderValues
import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.service.hmdb.HentProdukter
import no.nav.hjelpemidler.service.hmdb.hentprodukter.Product
import java.net.URL
import java.time.LocalDateTime

class HjelpemiddeldatabaseClient {
    private val logg = KotlinLogging.logger {}
    private val client =
        GraphQLKtorClient(
            url = URL("${Configuration.application["GRUNNDATA_SEARCH_URL"]!!}/graphql"),
            httpClient = HttpClient(engineFactory = Apache),
            serializer = GraphQLClientJacksonSerializer()
        )
    private val httpClient: HttpClient = HttpClient(engineFactory = Apache) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        expectSuccess = true
    }

    suspend fun hentProdukter(hmsnr: String): List<Product> =
        hentProdukter(setOf(hmsnr))

    suspend fun hentProdukter(hmsnrs: Set<String>): List<Product> {
        if (hmsnrs.isEmpty()) return emptyList()
        val request = HentProdukter(variables = HentProdukter.Variables(hmsnrs = hmsnrs.toList()))
        return try {
            val response = client.execute(request)
            when {
                response.errors != null -> {
                    throw Exception("Feil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs, errors=${response.errors?.map { it.message }}")
                }

                response.data != null -> response.data?.products ?: emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            throw RuntimeException("Nettverksfeil under henting av data fra hjelpemiddeldatabasen, hmsnrs=$hmsnrs", e)
        }
    }

    suspend fun hentAlleAvtaler(): List<Avtale> {
        val response = httpClient.post("${Configuration.application["GRUNNDATA_SEARCH_URL"]!!}/agreements/_search") {
            header(HttpHeaders.ContentType, HttpHeaderValues.APPLICATION_JSON)
            header(HttpHeaders.Accept, HttpHeaderValues.APPLICATION_JSON)
            setBody("""{ "query": { "bool": { "must": [ { "match": { "status": "ACTIVE" } } ] } } }""")
        }
        val result = response.body<OSResponse>()
        return result.hits?.hits?.map { it.avtale } ?: listOf()
    }
}

data class OSResponse(
    val hits: OSResponseHits?,
)

data class OSResponseHits(
    val hits: List<OSResponseHit>?,
)

data class OSResponseHit(
    @JsonAlias("_source")
    val avtale: Avtale,
)

data class Avtale(
    val id: String,
    val identifier: String,
    val title: String,
    val published: LocalDateTime,
    val expired: LocalDateTime,
)
