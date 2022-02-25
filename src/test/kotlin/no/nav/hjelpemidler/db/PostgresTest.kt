package no.nav.hjelpemidler.db

import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.hjelpemidler.configuration.Configuration
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

internal object PostgresContainer {
    val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:13.1").apply {
            start()
        }
    }
}

internal object DataSource {
    val instance: HikariDataSource by lazy {
        HikariDataSource().apply {
            username = PostgresContainer.instance.username
            password = PostgresContainer.instance.password
            jdbcUrl = PostgresContainer.instance.jdbcUrl
            connectionTimeout = 1000L
        }.also { sessionOf(it).run(queryOf("DROP ROLE IF EXISTS cloudsqliamuser").asExecute) }
            .also { sessionOf(it).run(queryOf("CREATE ROLE cloudsqliamuser").asExecute) }
    }
}

internal fun withCleanDb(test: () -> Unit) = DataSource.instance.also { clean(it) }
    .run { test() }

internal fun withMigratedDb(test: () -> Unit) =
    DataSource.instance.also { clean(it) }
        .also { migrate(it) }.run { test() }

internal class PostgresTest {
    @Test
    fun `JDBC url is set correctly from  config values `() {
        with(hikariConfigFrom(Configuration)) {
            jdbcUrl shouldBe "jdbc:postgresql://host.docker.internal:5434/hm-forslagsmotor-tilbehoer"
        }
    }
}
