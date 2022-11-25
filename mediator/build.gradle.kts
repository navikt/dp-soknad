buildscript { repositories { mavenCentral() } }

plugins {
    id("dagpenger.common")
    id("dagpenger.rapid-and-rivers")
}

dependencies {
    implementation(project(":modell"))
    implementation(Jackson.jsr310)
    implementation(Ktor2.Server.library("auth"))
    implementation(Ktor2.Server.library("auth-jwt"))
    implementation(Ktor2.Server.library("status-pages"))
    implementation(Ktor2.Server.library("call-logging"))
    implementation(Ktor2.Server.library("call-id"))
    implementation(Ktor2.Server.library("default-headers"))
    implementation(Ktor2.Client.library("cio"))
    implementation(Ktor2.Client.library("content-negotiation"))
    implementation(Ktor2.Server.library("content-negotiation"))
    implementation("io.prometheus:simpleclient_caffeine:0.16.0")
    implementation("io.ktor:ktor-serialization-jackson:${Ktor2.version}")
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:${Dagpenger.Biblioteker.version}")
    implementation("com.github.navikt.dp-biblioteker:pdl-klient:${Dagpenger.Biblioteker.version}")
    implementation("com.github.navikt:pam-geography:2.15")

    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:${Jackson.version}")
    // DB
    implementation(Database.Flyway)
    implementation(Database.HikariCP)
    implementation(Database.Postgres)
    implementation(Database.Kotlinquery)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")

    testImplementation(Ktor2.Server.library("test-host"))
    testImplementation(Ktor2.Client.library("mock"))
    testImplementation(Mockk.mockk)
    testImplementation(Junit5.params)
    testImplementation("no.nav.security:mock-oauth2-server:0.5.6")
    testImplementation("org.testcontainers:testcontainers:${TestContainers.version}")
    testImplementation(TestContainers.postgresql)
}

application {
    mainClass.set("no.nav.dagpenger.soknad.AppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
}
