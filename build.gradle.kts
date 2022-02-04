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
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2022.02.04-11.05.2e4e6cc859f7")
    implementation("com.github.navikt.dp-biblioteker:pdl-klient:2022.02.04-11.05.2e4e6cc859f7")

    implementation("io.lettuce:lettuce-core:6.1.6.RELEASE")

    testImplementation(Ktor.ktorTest)
    testImplementation(Mockk.mockk)
    testImplementation("no.nav.security:mock-oauth2-server:0.4.1")
    testImplementation("org.testcontainers:testcontainers:${TestContainers.version}")
}

application {
    mainClass.set("no.nav.dagpenger.quizshow.api.AppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}
