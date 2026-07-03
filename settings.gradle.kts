plugins {
    id("com.gradle.develocity") version "4.5.0"
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

include("micronaut-gradle-plugins")
include("micronaut-kotlin-build-plugins")
