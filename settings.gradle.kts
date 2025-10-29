plugins {
    id("com.gradle.develocity") version "4.2.2"
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "micronaut-build"

develocity {
    server = "https://ge.micronaut.io"
    buildScan {
        publishing {
            onlyIf { context -> context.isAuthenticated }
        }
    }
}

include("gradle-build-plugins")
include("kotlin-build-plugins")
