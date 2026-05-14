plugins {
    id("io.micronaut.build.internal.library")
    groovy
}

dependencies {
    implementation(libs.commons.logging)
    implementation(libs.commons.text)
    implementation(libs.snakeyaml)
    implementation(libs.radeox)
    implementation(libs.handlebars)
    implementation(libs.asciidoctorj)
    implementation(libs.tomlj)

    // We must differentiate the version that we use HERE to test the build plugins, which
    // should use a version of Spock which is compatible with what Gradle uses (Groovy 4)
    // and the version that we will use in Micronaut projects, which is going to be Groovy 5
    var localSpockVersion = libs.versions.spock.get().replace("groovy-5", "groovy-4")

    testImplementation(platform(libs.spock.bom)) {
        version {
            require(localSpockVersion)
        }
    }
    testImplementation(libs.spock.core) {
        version {
            require(localSpockVersion)
        }
    }
    testImplementation(libs.typesafe.config)
    testImplementation(localGroovy())
}

val docFilesJar = tasks.register<Jar>("docFilesJar") {
    description = "Package up files used for generating documentation."
    archiveVersion = null
    archiveFileName = "grails-doc-files.jar"
    from("src/main/template")
}

tasks.named<Jar>("jar") {
    from(docFilesJar)
}
