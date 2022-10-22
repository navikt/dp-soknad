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
    implementation("io.prometheus:simpleclient_caffeine:0.15.0")
    implementation("io.ktor:ktor-serialization-jackson:${Ktor2.version}")
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.10.22-09.05.6fcf3395aa4f")
    implementation("com.github.navikt.dp-biblioteker:pdl-klient:2022.10.22-09.05.6fcf3395aa4f")
    implementation("com.github.navikt:pam-geography:2.15")

    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.13.3")
    // DB
    implementation("org.flywaydb:flyway-core:8.5.0") // @todo update flyway in service-template
    implementation("com.zaxxer:HikariCP:5.0.1") //  @todo update HikariCP in service-template
    implementation("org.postgresql:postgresql:42.3.3") //  @todo update postgresql in service-template
    implementation(Database.Kotlinquery)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")

    testImplementation(Ktor2.Server.library("test-host"))
    testImplementation(Ktor2.Client.library("mock"))
    testImplementation(Mockk.mockk)
    testImplementation(Junit5.params)
    testImplementation("no.nav.security:mock-oauth2-server:0.5.5")
    testImplementation("org.testcontainers:testcontainers:${TestContainers.version}")
    testImplementation(TestContainers.postgresql)
    
}
application {
    mainClass.set("no.nav.dagpenger.soknad.AppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
}
