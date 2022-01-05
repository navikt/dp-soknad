buildscript { repositories { mavenCentral() } }

plugins {
    id("dagpenger.common")
    id("dagpenger.rapid-and-rivers")
}

dependencies {
    implementation(Ktor.library("websockets"))
    implementation(Ktor.library("jackson"))
    implementation(Ktor.library("auth"))
    implementation(Ktor.library("auth-jwt"))
    implementation(Ktor.library("client-cio"))
    implementation(Ktor.library("client-jackson"))

    implementation("com.github.ben-manes.caffeine:caffeine:3.0.5")

    testImplementation(Ktor.ktorTest)
    testImplementation(Mockk.mockk)
    testImplementation("no.nav.security:mock-oauth2-server:0.4.1")
}

application {
    mainClass.set("no.nav.dagpenger.quizshow.api.AppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}
