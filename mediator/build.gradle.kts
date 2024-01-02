plugins {
    id("common")
    application
}

val openTelemetryVersion = "1.32.0"

dependencies {
    implementation(project(path = ":modell"))

    implementation(libs.bundles.jackson)

    implementation(libs.bundles.postgres)

    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)

    implementation(libs.rapids.and.rivers)

    implementation(libs.konfig)
    implementation(libs.kotlin.logging)

    // OpenTelemetry tracing
    implementation("io.opentelemetry:opentelemetry-api:$openTelemetryVersion")
    implementation("io.opentelemetry:opentelemetry-api:$openTelemetryVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$openTelemetryVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-2.0:$openTelemetryVersion-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-kafka-clients-2.6:$openTelemetryVersion-alpha")

    implementation("com.github.navikt:pam-geography:2.21")
    implementation(libs.dp.biblioteker.oauth2.klient)
    implementation(libs.dp.biblioteker.pdl.klient)
    implementation(libs.micrometer.registry.prometheus)
    implementation("io.prometheus:simpleclient_caffeine:0.16.0")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.16.1")

    testImplementation(libs.ktor.client.mock)
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")

    testImplementation("io.mockk:mockk:${libs.versions.mockk.get()}")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.bundles.postgres.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation(libs.ktor.client.mock)
}

application {
    mainClass.set("no.nav.dagpenger.soknad.AppKt")
}
