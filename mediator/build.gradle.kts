plugins {
    id("common")
    application
}

dependencies {
    implementation(project(path = ":modell"))

    implementation(libs.bundles.jackson)

    implementation(libs.bundles.postgres)

    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)

    implementation(libs.rapids.and.rivers)

    implementation(libs.konfig)
    implementation(libs.kotlin.logging)

    implementation("com.github.navikt:pam-geography:2.15")
    implementation(libs.dp.biblioteker.oauth2.klient)
    implementation(libs.dp.biblioteker.pdl.klient)
    implementation(libs.prometheus.client)
    implementation(libs.prometheus.simpleclient)
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:0.0.1")

    testImplementation(libs.ktor.client.mock)
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")

    testImplementation("io.mockk:mockk:${libs.versions.mockk.get()}")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.bundles.postgres.test)
    testImplementation(libs.ktor.server.test.host.jvm)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation(libs.ktor.client.mock)

//    implementation(Ktor2.Server.library("auth"))
//    implementation(Ktor2.Server.library("auth-jwt"))
//    implementation(Ktor2.Server.library("status-pages"))
//    implementation(Ktor2.Server.library("call-logging"))
//    implementation(Ktor2.Server.library("call-id"))
//    implementation(Ktor2.Server.library("default-headers"))
//    implementation(Ktor2.Client.library("cio"))
//    implementation(Ktor2.Client.library("content-negotiation"))
//    implementation(Ktor2.Client.library("logging"))
//    implementation(Ktor2.Server.library("content-negotiation"))
//    implementation("io.prometheus:simpleclient_caffeine:0.16.0")
//    implementation("io.ktor:ktor-serialization-jackson:${Ktor2.version}")
//    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:${Dagpenger.Biblioteker.version}")
//    implementation("com.github.navikt.dp-biblioteker:pdl-klient:${Dagpenger.Biblioteker.version}")
//    implementation("com.github.navikt:pam-geography:2.15")
//
//    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:${Jackson.version}")
//    // DB
//    implementation(Database.Flyway)
//    implementation(Database.HikariCP)
//    implementation(Database.Postgres)
//    implementation(Database.Kotlinquery)
//    implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")
//
//    testImplementation(Ktor2.Server.library("test-host"))
//    testImplementation(Ktor2.Client.library("mock"))
//    testImplementation(Mockk.mockk)
//    testImplementation(Junit5.params)
//    testImplementation("no.nav.security:mock-oauth2-server:0.5.6")
//    testImplementation("org.testcontainers:testcontainers:${TestContainers.version}")
//    testImplementation(TestContainers.postgresql)
}

application {
    mainClass.set("no.nav.dagpenger.soknad.AppKt")
}
