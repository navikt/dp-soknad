plugins {
    id("common")
}

dependencies {

    implementation(libs.bundles.jackson)
    api("de.slub-dresden:urnlib:2.0.1")

    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")

    testImplementation("io.mockk:mockk:${libs.versions.mockk.get()}")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.ktor.server.test.host.jvm)
    testImplementation("com.approvaltests:approvaltests:22.3.2")
}
