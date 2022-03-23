buildscript { repositories { mavenCentral() } }

plugins {
    id("dagpenger.common")
    id("dagpenger.rapid-and-rivers")
}

dependencies {
    implementation(Ktor.library("jackson"))
    implementation(Jackson.jsr310)
    implementation(Ktor.library("auth"))
    implementation(Ktor.library("auth-jwt"))
    implementation(Ktor.library("client-cio"))
    implementation(Ktor.library("client-jackson"))
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.02.05-16.32.da1deab37b31")
    implementation("com.github.navikt.dp-biblioteker:pdl-klient:2022.02.05-16.32.da1deab37b31")
    implementation("com.github.navikt:pam-geography:2.15")

    // DB
    implementation("org.flywaydb:flyway-core:8.5.0") // @todo update flyway in service-template
    implementation("com.zaxxer:HikariCP:5.0.1") //  @todo update HikariCP in service-template
    implementation("org.postgresql:postgresql:42.3.3") //  @todo update postgresql in service-template
    implementation(Database.Kotlinquery)

    testImplementation(Ktor.ktorTest)
    testImplementation(Ktor.library("client-mock"))
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
