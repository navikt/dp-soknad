plugins {
    id("org.openapi.generator") version "7.12.0"
    id("common")
    `java-library`
}

tasks.named("compileKotlin").configure {
    dependsOn("openApiGenerate")
}

tasks.named("runKtlintCheckOverMainSourceSet").configure {
    dependsOn("openApiGenerate")
}

tasks.named("runKtlintFormatOverMainSourceSet").configure {
    dependsOn("openApiGenerate")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/kotlin", "${layout.buildDirectory.get()}/generated/src/main/kotlin"))
        }
    }
}

ktlint {
    filter {
        exclude { element -> element.file.path.contains("generated/") }
    }
}

dependencies {
    implementation(libs.jackson.annotation)
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/src/main/resources/soknad-api.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/")
    packageName.set("no.nav.dagpenger.soknad.api")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
        ),
    )
    typeMappings.set(
        mapOf(
            "DateTime" to "LocalDateTime",
        ),
    )

    importMappings.set(
        mapOf(
            "LocalDateTime" to "java.time.LocalDateTime",
        ),
    )

    modelNameSuffix.set("Response")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "enumPropertyNaming" to "original",
            "dateLibrary" to "custom",
        ),
    )
}
