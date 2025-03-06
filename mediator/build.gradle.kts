import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("common")
    application
    alias(libs.plugins.shadow.jar)
}

dependencies {
    implementation(project(path = ":modell"))
    implementation(project(path = ":openapi"))

    implementation(libs.bundles.jackson)

    implementation(libs.bundles.postgres)

    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)

    implementation(libs.rapids.and.rivers)

    implementation(libs.konfig)
    implementation(libs.kotlin.logging)

    implementation("com.github.navikt:pam-geography:2.21")
    implementation("no.nav.dagpenger:pdl-klient:2025.02.13-18.02.052b7c34baab")
    implementation("no.nav.dagpenger:oauth2-klient:2025.02.13-18.02.052b7c34baab")
    implementation("io.prometheus:simpleclient_caffeine:0.16.0")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.18.3")

    testImplementation(libs.ktor.client.mock)
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")

    testImplementation("io.mockk:mockk:${libs.versions.mockk.get()}")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.bundles.postgres.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.12.0")
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.rapids.and.rivers.test)
}

application {
    mainClass.set("no.nav.dagpenger.soknad.AppKt")
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}
