rootProject.name = "dp-soknad"

include(
    "modell",
    "mediator",
)

dependencyResolutionManagement {
    repositories {
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.dagpenger:dp-version-catalog:20231229.65.2c2e9f")
        }
    }
}
