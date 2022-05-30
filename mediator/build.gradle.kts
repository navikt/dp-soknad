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
    implementation(Ktor2.Server.library("default-headers"))
    implementation(Ktor2.Client.library("cio"))
    implementation(Ktor2.Client.library("content-negotiation"))
    implementation(Ktor2.Server.library("content-negotiation"))
    implementation("io.ktor:ktor-serialization-jackson:${Ktor2.version}")
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.05.30-09.37.623ee13a49dd")
    implementation("com.github.navikt.dp-biblioteker:pdl-klient:2022.05.30-09.37.623ee13a49dd")
    implementation("com.github.navikt:pam-geography:2.15")

    // DB
    implementation("org.flywaydb:flyway-core:8.5.0") // @todo update flyway in service-template
    implementation("com.zaxxer:HikariCP:5.0.1") //  @todo update HikariCP in service-template
    implementation("org.postgresql:postgresql:42.3.3") //  @todo update postgresql in service-template
    implementation(Database.Kotlinquery)

    testImplementation(Ktor2.Server.library("test-host"))
    testImplementation(Ktor2.Client.library("mock"))
    testImplementation(Mockk.mockk)
    testImplementation(Junit5.params)
    testImplementation("no.nav.security:mock-oauth2-server:0.4.1")
    testImplementation("org.testcontainers:testcontainers:${TestContainers.version}")
    testImplementation(TestContainers.postgresql)
}

application {
    mainClass.set("no.nav.dagpenger.soknad.AppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}
