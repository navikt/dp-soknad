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
    implementation("io.lettuce:lettuce-core:6.1.6.RELEASE")

    testImplementation(Ktor.ktorTest)
    testImplementation(Ktor.library("client-mock"))
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
