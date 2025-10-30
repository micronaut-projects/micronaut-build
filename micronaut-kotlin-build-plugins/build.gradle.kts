plugins {
    id("io.micronaut.build.internal.gradle-plugin")
    groovy
}

micronautBuildPlugin {
    versionsFullyQualifiedClassName = "io.micronaut.build.kotlin.DefaultVersions"
    definePlugin("kotlin", "io.micronaut.build.kotlin.MicronautBuildKotlinPlugin")
    definePlugin("kotlin-kapt", "io.micronaut.build.kotlin.MicronautBuildKotlinKaptPlugin")
    definePlugin("kotlin-ksp", "io.micronaut.build.kotlin.MicronautBuildKotlinKspPlugin")
}

dependencies {
    implementation(projects.micronautGradlePlugins)
    testImplementation(platform(libs.spock.bom))
    testImplementation(libs.spock.core)
    implementation(libs.kotlin.jvm.plugin)
    implementation(libs.kotlin.kapt.plugin)
    implementation(libs.kotlin.ksp.plugin)
}
