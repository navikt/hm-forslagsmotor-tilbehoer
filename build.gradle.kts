
import Build_gradle.Versions.ktlint_version
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

val kotlin_version by extra("1.6.10")

object Versions {
    const val rapid_version = "20210617121814-3e67e4d"
    const val kafka_version = "3.1.0"
    const val influxdb_version = "2.22"
    const val influxdb_aiven_version = "4.2.0"
    const val unleash_version = "4.4.1"
    const val junit_version = "5.8.2"

    const val ktlint_version = "0.38.1"

    // Ktor
    const val ktor_version = "1.6.7"
    const val ktor_server_netty = "io.ktor:ktor-server-netty:$ktor_version"
    const val ktor_ktor_test = "io.ktor:ktor-server-test-host:$ktor_version"

    // Jackson
    const val jackson_version = "2.13.1"
    const val jackson_core = "com.fasterxml.jackson.core:jackson-core:$jackson_version"
    const val jackson_kotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version"
    const val jackson_jsr310 = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson_version"

    // Fuel
    const val fuel_version = "2.2.1"
    const val fuel_fuel = "com.github.kittinunf.fuel:fuel:$fuel_version"
    fun fuel_library(name: String) = "com.github.kittinunf.fuel:fuel-$name:$fuel_version"
    // const val fuel_fuel_moshi = "com.github.kittinunf.fuel:fuel-moshi:$fuel_version"

    // Konfig
    const val konfig_version = "1.6.10.0"
    const val konfig_konfig = "com.natpryce:konfig:$konfig_version"

    // Kotlin
    const val kotlin_version = "1.6.10"
    const val kotlin_test_junit5 = "org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version"

    // Kotlin logging
    const val kotlin_logging_version = "2.1.21"
    const val kotlin_logging_kotlin_logging = "io.github.microutils:kotlin-logging:$kotlin_logging_version"

    // Micrometer
    const val micrometer_version = "1.4.0"
    const val micrometer_prometheus_registry = "io.micrometer:micrometer-registry-prometheus:$micrometer_version"

    // GraphQL
    const val graphql_version = "5.2.0"
    const val graphql_graphql = "com.expediagroup.graphql"
    val graphql_ktor_client = graphql_library("ktor-client")
    val graphql_client_jackson = graphql_library("client-jackson")
    fun graphql_library(name: String) = "com.expediagroup:graphql-kotlin-$name:$graphql_version"

    // Spotless
    const val spotless_version = "6.2.1"
    const val spotless_spotless = "com.diffplug.spotless"

    // Shadow
    const val shadow_version = "7.1.2"
    const val shadow_shadow = "com.github.johnrengelman.shadow"

    // Database
    const val postgres_postgres = "org.postgresql:postgresql:42.3.2"
    const val kotlinquery_kotlinquery = "com.github.seratch:kotliquery:1.3.1"
    const val flyway_flyway = "org.flywaydb:flyway-core:8.4.4"
    const val hikaricp_hikaricp = "com.zaxxer:HikariCP:5.0.1"

    // KoTest
    const val kotest_version = "5.1.0"
    const val kotest_runner = "io.kotest:kotest-runner-junit5-jvm:$kotest_version" // for kotest framework
    const val kotest_assertions = "io.kotest:kotest-assertions-core-jvm:$kotest_version" // for kotest core jvm assertion

    // Mockk
    const val mockk_version = "1.10.0"
    const val mockk_mockk = "io.mockk:mockk:$mockk_version"

    // TestContainers
    const val testcontainers_version = "1.16.3"
    const val testcontainers_postgresql = "org.testcontainers:postgresql:$testcontainers_version"

    // Wiremock
    const val wiremock_version = "2.21.0"
    const val wiremock_standalone = "com.github.tomakehurst:wiremock-standalone:$wiremock_version"
}

plugins {
    application
    kotlin("jvm") version "1.6.10"
    id("com.expediagroup.graphql") version "5.2.0"
    id("com.diffplug.spotless") version "6.2.1"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

/* apply {
    plugin(Spotless.spotless)
} */

application {
    applicationName = "hm-forslagsmotor-tilbehoer"
    mainClass.set("no.nav.hjelpemidler.ApplicationKt")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // Used for Rapids and rivers-dependency
    maven("https://packages.confluent.io/maven/") // Kafka-avro
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api("ch.qos.logback:logback-classic:1.2.10")
    api("net.logstash.logback:logstash-logback-encoder:7.0.1") {
        exclude("com.fasterxml.jackson.core")
    }

    implementation(Versions.jackson_core)
    implementation(Versions.jackson_kotlin)
    implementation(Versions.jackson_jsr310)
    implementation(Versions.ktor_server_netty)
    constraints {
        implementation("io.netty:netty-codec-http2:4.1.73.Final") {
            because("Snyk - Medium Severity - HTTP Request Smuggling")
        }
    }
    implementation(Versions.fuel_fuel)
    implementation(Versions.fuel_library("coroutines"))
    implementation(Versions.konfig_konfig)
    implementation(Versions.kotlin_logging_kotlin_logging)

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.guepardoapps:kulid:1.1.2.0")
    implementation("io.ktor:ktor-jackson:${Versions.ktor_version}")
    implementation("io.ktor:ktor-auth:${Versions.ktor_version}")
    implementation("io.ktor:ktor-auth-jwt:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktor_version}")
    constraints {
        implementation("org.apache.httpcomponents:httpclient:4.5.13") {
            because("Snyk - Medium Severity - Improper Input Validation")
        }
    }
    implementation("io.ktor:ktor-client-jackson:${Versions.ktor_version}")
    implementation("com.github.navikt:rapids-and-rivers:${Versions.rapid_version}")
    implementation("org.apache.kafka:kafka-clients:${Versions.kafka_version}")
    implementation("org.influxdb:influxdb-java:${Versions.influxdb_version}")
    implementation("com.influxdb:influxdb-client-kotlin:${Versions.influxdb_aiven_version}")
    implementation("no.finn.unleash:unleash-client-java:${Versions.unleash_version}")
    constraints {
        implementation("com.google.code.gson:gson:2.8.9") {
            because("Snyk reported High Severity issue- Deserialization of Untrusted Data ")
        }
    }
    implementation("io.ktor:ktor-client-auth-jvm:${Versions.ktor_version}")
    implementation(Versions.micrometer_prometheus_registry)
    implementation(Versions.graphql_ktor_client) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
        exclude("io.ktor", "ktor-client-cio") // prefer ktor-client-apache
    }
    implementation(Versions.graphql_client_jackson)

    // Database
    implementation(Versions.postgres_postgres)
    implementation(Versions.hikaricp_hikaricp)
    implementation(Versions.flyway_flyway)
    implementation(Versions.kotlinquery_kotlinquery)

    testImplementation(Versions.kotlin_test_junit5)
    testImplementation(Versions.kotest_assertions)
    testImplementation(Versions.kotest_runner)
    testImplementation(Versions.ktor_ktor_test)
    testImplementation(Versions.mockk_mockk)
    testImplementation(Versions.testcontainers_postgresql)
    testImplementation(Versions.wiremock_standalone)

    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.junit_version}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Versions.junit_version}")
}

spotless {
    kotlin {
        ktlint(ktlint_version)
        targetExclude("**/generated/**")
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/*.gradle.kts")
        ktlint(ktlint_version)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf()
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true
        outputs.upToDateWhen { false }
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "7.4"
}

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("jar") {
    dependsOn("test")
}

tasks.named("compileKotlin") {
    dependsOn("spotlessApply")
    dependsOn("spotlessCheck")
}

graphql {
    client {
        schemaFile = file("src/main/resources/hmdb/schema.graphql")
        queryFileDirectory = "src/main/resources/hmdb"
        packageName = "no.nav.hjelpemidler.service.hmdb"
    }
}

val graphqlIntrospectSchema by tasks.getting(GraphQLIntrospectSchemaTask::class) {
    endpoint.set("https://hm-grunndata-api.dev.intern.nav.no/graphql")
    outputFile.set(file("src/main/resources/hmdb/schema.graphql"))
}
