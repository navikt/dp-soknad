buildscript { repositories { mavenCentral() } }

plugins {
    id("dagpenger.common")
    id("dagpenger.rapid-and-rivers")
}

dependencies {
    implementation(Ktor.library("websockets"))

    implementation("com.github.ben-manes.caffeine:caffeine:3.0.4")

    testImplementation(Ktor.ktorTest)
}

application {
    mainClass.set("no.nav.dagpenger.quizshow.api.AppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}
