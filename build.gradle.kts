import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

val rapid_version: String by project
val kafka_version: String by project
val influxdb_version: String by project
val influxdb_aiven_version: String by project
val unleash_version: String by project
val junit_version: String by project
val ktor_version = Ktor.version

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(GraphQL.graphql) version GraphQL.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

apply {
    plugin(Spotless.spotless)
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
    api("ch.qos.logback:logback-classic:1.2.10")
    api("net.logstash.logback:logstash-logback-encoder:7.0.1") {
        exclude("com.fasterxml.jackson.core")
    }

    implementation(Jackson.core)
    implementation(Jackson.kotlin)
    implementation(Jackson.jsr310)
    implementation(Ktor.serverNetty)
    constraints {
        implementation("io.netty:netty-codec-http2:4.1.73.Final") {
            because("Snyk - Medium Severity - HTTP Request Smuggling")
        }
    }
    implementation(Fuel.fuel)
    implementation(Fuel.library("coroutines"))
    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.guepardoapps:kulid:1.1.2.0")
    implementation("io.ktor:ktor-jackson:$ktor_version")
    implementation("io.ktor:ktor-auth:$ktor_version")
    implementation("io.ktor:ktor-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-client-apache:$ktor_version")
    constraints {
        implementation("org.apache.httpcomponents:httpclient:4.5.13") {
            because("Snyk - Medium Severity - Improper Input Validation")
        }
    }
    implementation("io.ktor:ktor-client-jackson:$ktor_version")
    implementation("com.github.navikt:rapids-and-rivers:$rapid_version")
    implementation("org.apache.kafka:kafka-clients:$kafka_version")
    implementation("org.influxdb:influxdb-java:$influxdb_version")
    implementation("com.influxdb:influxdb-client-kotlin:$influxdb_aiven_version")
    implementation("no.finn.unleash:unleash-client-java:$unleash_version")
    constraints {
        implementation("com.google.code.gson:gson:2.8.9") {
            because("Snyk reported High Severity issue- Deserialization of Untrusted Data ")
        }
    }
    implementation("io.ktor:ktor-client-auth-jvm:$ktor_version")
    implementation(Micrometer.prometheusRegistry)
    implementation("org.influxdb:influxdb-java:$influxdb_version")
    implementation("com.influxdb:influxdb-client-kotlin:$influxdb_aiven_version")
    implementation(GraphQL.ktorClient) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
        exclude("io.ktor", "ktor-client-cio") // prefer ktor-client-apache
    }
    implementation(GraphQL.clientJackson)

    testImplementation(Kotlin.testJUnit5)
    testImplementation(KoTest.assertions)
    testImplementation(KoTest.runner)
    testImplementation(Ktor.ktorTest)
    testImplementation(Mockk.mockk)
    testImplementation(TestContainers.postgresql)
    testImplementation(Wiremock.standalone)

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
}

spotless {
    kotlin {
        ktlint(Ktlint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/*.gradle.kts")
        ktlint(Ktlint.version)
    }
}

/*tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf()
    kotlinOptions.jvmTarget = "11"
}*/
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
    gradleVersion = "7.3.3"
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
