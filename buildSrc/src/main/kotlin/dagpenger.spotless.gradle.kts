import com.diffplug.spotless.LineEnding.PLATFORM_NATIVE
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        ktlint("0.50.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("0.50.0")
    }

    // Workaround for <https://github.com/diffplug/spotless/issues/1644>
    // using idea found at
    // <https://github.com/diffplug/spotless/issues/1527#issuecomment-1409142798>.
    lineEndings = PLATFORM_NATIVE // or any other except GIT_ATTRIBUTES
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("spotlessApply")
}
