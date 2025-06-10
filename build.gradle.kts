import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

plugins {
    application
    kotlin("jvm") version "1.9.0"
    id("com.diffplug.spotless") version "6.2.1"
    id("com.expediagroup.graphql") version "6.4.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

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
    implementation(kotlin("stdlib-jdk8"))

    // R&R
    implementation("com.github.navikt:rapids-and-rivers:2023101613431697456627.0cdd93eb696f") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "net.logstash.logback", module = "logstash-logback-encoder")
    }

    // Jackson
    val jackson_version = "2.14.2"
    implementation("com.fasterxml.jackson.core:jackson-core:$jackson_version")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson_version")

    // Ktor
    val ktor_version = "2.3.3"
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-apache:$ktor_version")
    implementation("io.ktor:ktor-client-jackson:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-jackson:$ktor_version")

    implementation("io.ktor:ktor-server-netty:$ktor_version")
    constraints {
        implementation("io.netty:netty-codec-http2:4.1.100.Final") {
            because("io.netty:netty-codec-http2 vulnerable to HTTP/2 Rapid Reset Attack, before 4.1.100.Final")
        }
    }

    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    constraints {
        implementation("com.auth0:jwks-rsa:0.22.1") {
            because("Guava vulnerable to insecure use of temporary directory (<32.0.0)")
        }
    }

    // Logging
    api("ch.qos.logback:logback-classic:1.4.12")
    api("net.logstash.logback:logstash-logback-encoder:7.3") {
        exclude("com.fasterxml.jackson.core")
    }
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    // GraphQL
    val graphql_version = "6.4.0"
    implementation("com.expediagroup:graphql-kotlin-ktor-client:$graphql_version") {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
        exclude("io.ktor", "ktor-client-cio") // prefer ktor-client-apache
    }
    implementation("com.expediagroup:graphql-kotlin-client-jackson:$graphql_version")

    // Cache
    implementation("javax.cache:cache-api:1.1.1")
    implementation("org.ehcache:ehcache:3.10.6")

    // Database
    implementation("org.postgresql:postgresql:42.6.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.flywaydb:flyway-core:9.16.0")
    implementation("com.github.seratch:kotliquery:1.9.0")

    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("org.apache.kafka:kafka-clients:3.9.1")

    constraints {
        implementation("com.squareup.okio:okio:3.4.0") {
            because("Okio Signed to Unsigned Conversion Error vulnerability, before 3.4.0")
        }
    }

    constraints {
        implementation("com.google.code.gson:gson:2.8.9") {
            because("Snyk reported High Severity issue- Deserialization of Untrusted Data ")
        }
    }
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.5")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.6.10")
    val kotest_version = "5.5.5"
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotest_version")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotest_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("org.testcontainers:postgresql:1.20.6")
    val junit_version = "5.9.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
}

val ktlint_version = "0.43.2"
spotless {
    kotlin {
        trimTrailingWhitespace()
        indentWithSpaces()
        ktlint(ktlint_version)
        targetExclude("build/**/*")
    }
    kotlinGradle {
        target("*.gradle.kts")
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
        schemaFile = file("src/main/resources/hmdb/schema.graphqls")
        queryFileDirectory = "src/main/resources/hmdb"
        packageName = "no.nav.hjelpemidler.service.hmdb"
    }
}

val graphqlIntrospectSchema by tasks.getting(GraphQLIntrospectSchemaTask::class) {
    endpoint.set("https://hm-grunndata-search.intern.dev.nav.no/graphql")
    outputFile.set(file("src/main/resources/hmdb/schema.graphqls"))
}
