plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.1.20"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
}
