plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
}
