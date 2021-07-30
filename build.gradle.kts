import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

val rapid_version: String by project
val logging_version: String by project
val konfig_version: String by project
val brukernotifikasjon_schemas_version: String by project
val kafka_version: String by project
val kafka_avro_version: String by project
val influxdb_version: String by project
val unleash_version: String by project
val wiremock_version: String by project
val ktlint_version: String by project
val junit_version: String by project
val mockk_version: String by project
val kotest_version: String by project
val kotlin_test_version: String by project

plugins {
    application
    kotlin("jvm") version "1.4.21"
    id("com.diffplug.spotless") version "5.11.0"
}

apply {
    plugin("com.diffplug.spotless")
}

application {
    mainClass.set("no.nav.hjelpemidler.ApplicationKt")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // Used for Rapids and rivers-dependency
    maven("https://packages.confluent.io/maven/") // Kafka-avro
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:$rapid_version")
    implementation("com.natpryce:konfig:$konfig_version")
    implementation("io.github.microutils:kotlin-logging:$logging_version")
    implementation("com.github.navikt:brukernotifikasjon-schemas:$brukernotifikasjon_schemas_version")
    implementation("org.apache.kafka:kafka-clients:$kafka_version")
    implementation("io.confluent:kafka-avro-serializer:$kafka_avro_version")
    implementation("org.influxdb:influxdb-java:$influxdb_version")
    implementation("no.finn.unleash:unleash-client-java:$unleash_version")
    implementation("com.github.tomakehurst:wiremock-standalone:$wiremock_version")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotest_version")
    testImplementation("io.mockk:mockk:$mockk_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_test_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
    testRuntimeOnly("io.kotest:kotest-assertions-core-jvm:$kotest_version")
}

spotless {
    kotlin {
        ktlint(ktlint_version).userData(mapOf("disabled_rules" to "no-wildcard-imports"))
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/*.gradle.kts")
        ktlint(ktlint_version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "15"
}

val fatJar = task("fatJar", type = Jar::class) {
    baseName = "${project.name}-fat"
    manifest {
        attributes["Main-Class"] = "no.nav.hjelpemidler.ApplicationKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}
