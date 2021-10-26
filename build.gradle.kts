buildscript { repositories { mavenCentral() } }

plugins {
    id("dagpenger.common")
    id("dagpenger.rapid-and-rivers")
}

dependencies {
    implementation(Ktor.library("websockets"))
    testImplementation(Ktor.ktorTest)
}

application {
    mainClass.set("no.nav.dagpenger.quizshow.api.AppKt")
}
