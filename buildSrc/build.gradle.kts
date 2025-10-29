plugins {
    id("java-gradle-plugin")
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.testlogger.plugin)
    implementation(libs.nexus.publish.plugin)
}
