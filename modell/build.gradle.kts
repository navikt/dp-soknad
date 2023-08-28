plugins {
    id("dagpenger.common")
}

dependencies {
    implementation(Jackson.core)
    implementation(Jackson.kotlin)
    implementation(Jackson.jsr310)
    api("de.slub-dresden:urnlib:2.0.1")
    api("no.bekk.bekkopen:nocommons:0.15.0")

    testImplementation(Junit5.params)
    testImplementation(Mockk.mockk)
    testImplementation("com.approvaltests:approvaltests:11.2.3")
}
