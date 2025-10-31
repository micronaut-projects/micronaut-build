import io.github.gradlenexus.publishplugin.InitializeNexusStagingRepository

plugins {
    `java-gradle-plugin`
}

val functionalTest by sourceSets.creating {

}

configurations {
    "functionalTestImplementation" {
        extendsFrom(testImplementation.get())
    }
    "functionalTestRuntimeClasspath" {
        extendsFrom(testRuntimeOnly.get())
    }
}

gradlePlugin.testSourceSets(functionalTest)

val functionalTestTask = tasks.register<Test>("functionalTest") {
    inputs.dir(file("src/functionalTest/gradle-projects"))
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    systemProperty("CURRENT_JDK", JavaVersion.current().majorVersion)
    environment("GH_USERNAME", providers.environmentVariable("GH_USERNAME").getOrElse(""))
    environment("GH_TOKEN_PUBLIC_REPOS_READONLY", providers.environmentVariable("GH_TOKEN_PUBLIC_REPOS_READONLY").getOrElse(""))
    if (System.getenv("CI") != null) {
        environment("CI", providers.environmentVariable("CI").get())
    }
    // Workaround for SSL context initializer
    forkEvery = 1
    maxParallelForks = 4
}

tasks.named("check") {
    dependsOn(functionalTestTask)
}

//do not generate extra load on Nexus with new staging repository if signing fails
tasks.withType<InitializeNexusStagingRepository>().configureEach {
    shouldRunAfter(tasks.withType<Sign>())
}
