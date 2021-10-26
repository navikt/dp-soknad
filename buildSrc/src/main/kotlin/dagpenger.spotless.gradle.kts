import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        ktlint("0.40.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("0.40.0")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("spotlessApply")
}
