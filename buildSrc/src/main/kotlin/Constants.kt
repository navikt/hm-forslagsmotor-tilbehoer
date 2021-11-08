object Database {
    const val Postgres = "org.postgresql:postgresql:42.2.11"
    const val Kotlinquery = "com.github.seratch:kotliquery:1.3.1"
    const val Flyway = "org.flywaydb:flyway-core:6.3.2"
    const val HikariCP = "com.zaxxer:HikariCP:3.4.1"
}

object Fuel {
    const val version = "2.2.1"
    const val fuel = "com.github.kittinunf.fuel:fuel:$version"
    const val fuelMoshi = "com.github.kittinunf.fuel:fuel-moshi:$version"
    fun library(name: String) = "com.github.kittinunf.fuel:fuel-$name:$version"
}

object Jackson {
    const val version = "2.10.3"
    const val core = "com.fasterxml.jackson.core:jackson-core:$version"
    const val kotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:$version"
    const val jsr310 = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$version"
}

object Konfig {
    const val konfig = "com.natpryce:konfig:1.6.10.0"
}

object Kotlin {
    const val version = "1.5.31"
    const val stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"
    const val testJUnit5 = "org.jetbrains.kotlin:kotlin-test-junit5:$version"

    object Logging {
        const val version = "1.7.9"
        const val kotlinLogging = "io.github.microutils:kotlin-logging:$version"
    }
}

object KoTest {
    const val version = "4.2.0.RC2"

    // for kotest framework
    const val runner = "io.kotest:kotest-runner-junit5-jvm:$version"

    // for kotest core jvm assertion
    const val assertions = "io.kotest:kotest-assertions-core-jvm:$version"

    // for kotest property test
    const val property = "io.kotest:kotest-property-jvm:$version"

    // any other library
    fun library(name: String) = "io.kotest:kotest-$name:$version"
}

object Ktor {
    const val version = "1.6.4"
    const val server = "io.ktor:ktor-server:$version"
    const val serverNetty = "io.ktor:ktor-server-netty:$version"
    const val auth = "io.ktor:ktor-auth:$version"
    const val authJwt = "io.ktor:ktor-auth-jwt:$version"
    const val locations = "io.ktor:ktor-locations:$version"
    const val micrometerMetrics = "io.ktor:ktor-metrics-micrometer:$version"
    const val ktorTest = "io.ktor:ktor-server-test-host:$version"
    fun library(name: String) = "io.ktor:ktor-$name:$version"
}

object Micrometer {
    const val version = "1.4.0"
    const val prometheusRegistry = "io.micrometer:micrometer-registry-prometheus:$version"
}

object Mockk {
    const val version = "1.10.0"
    const val mockk = "io.mockk:mockk:$version"
}

object Ktlint {
    const val version = "0.38.1"
}

object Spotless {
    const val version = "5.1.0"
    const val spotless = "com.diffplug.spotless"
}

object Shadow {
    const val version = "5.2.0"
    const val shadow = "com.github.johnrengelman.shadow"
}

object TestContainers {
    const val version = "1.16.0"
    const val postgresql = "org.testcontainers:postgresql:$version"
}

object Ulid {
    const val version = "8.2.0"
    const val ulid = "de.huxhorn.sulky:de.huxhorn.sulky.ulid:$version"
}

object Wiremock {
    const val version = "2.21.0"
    const val standalone = "com.github.tomakehurst:wiremock-standalone:$version"
}

object GraphQL {
    const val version = "5.2.0"
    const val graphql = "com.expediagroup.graphql"
    val ktorClient = library("ktor-client")
    val clientJackson = library("client-jackson")
    fun library(name: String) = "com.expediagroup:graphql-kotlin-$name:$version"
}
