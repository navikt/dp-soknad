import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

repositories {
    jcenter()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

application {
    applicationName = "dp-quizshow-api"
    mainClassName = "no.nav.dagpenger.quizshow.api.AppKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(Konfig.konfig)

    testImplementation(kotlin("test-junit5"))
    testImplementation(Junit5.api)
    testRuntimeOnly(Junit5.engine)

    implementation(Ktor.library("jackson"))
    implementation(Ktor.library("websockets"))

    implementation("com.github.navikt:rapids-and-rivers:1.20a7b92")

    implementation(Kotlin.Logging.kotlinLogging)

    testImplementation(Ktor.ktorTest)
}

spotless {
    kotlin {
        ktlint(Ktlint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.kt*")
        ktlint(Ktlint.version)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("jar") {
    dependsOn("test")
}

/*tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}*/

tasks.withType<Wrapper> {
    gradleVersion = "6.0.1"
}
