package no.nav.hjelpemidler.db

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

object TestDatabase {

    private val postgresContainer: PostgreSQLContainer<Nothing> by lazy {
        PostgreSQLContainer<Nothing>("postgres:13.1").apply {
            waitingFor(Wait.forListeningPort())
            start()
        }
    }

    val datasource by lazy {
        val ds = HikariDataSource().apply {
            username = postgresContainer.username
            password = postgresContainer.password
            jdbcUrl = postgresContainer.jdbcUrl
            connectionTimeout = 1000L
        }.also {
            it.connection.prepareStatement("DROP ROLE IF EXISTS cloudsqliamuser").execute()
            it.connection.prepareStatement("CREATE ROLE cloudsqliamuser").execute()
        }
        cleanAndMigrate(ds)
    }

    private fun clean(ds: HikariDataSource) =
        Flyway.configure().cleanDisabled(false).dataSource(ds).load().clean()

    fun cleanAndMigrate(ds: HikariDataSource): HikariDataSource {
        clean(ds)
        migrate(ds)
        return ds
    }
}
