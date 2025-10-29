import gradle.kotlin.dsl.accessors._3450bfa35e4df7ed8039ccdc88fb5a2a.gradlePlugin
import io.micronaut.build.MicronautBuildPluginExtension
import io.micronaut.internal.VersionsWriterTask

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.adarshr.test-logger")
    id("signing")
    id("io.micronaut.build.internal.functional-testing")
}

version = project.extra.get("projectVersion") as String
group = "io.micronaut.build.internal"

val pluginExtension = extensions.create<MicronautBuildPluginExtension>("micronautBuildPlugin", gradlePlugin)

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    repositories {
        maven {
            name = "Build"
            url = uri(rootProject.layout.buildDirectory.dir("repo"))
        }
    }
}

val keyId = providers.environmentVariableOrSystemProperty("GPG_KEY_ID", "signing.keyId")
val keyPassword = providers.environmentVariableOrSystemProperty("GPG_PASSWORD", "signing.password")
extra.set("signing.keyId", keyId.orNull)
extra.set("signing.password", keyPassword.orNull)
if (file("${rootDir}/secring.gpg").exists()) {
    extra.set("signing.secretKeyRingFile", file("${rootDir}/secring.gpg").absolutePath)
} else if (file("${System.getenv("HOME")}/.gnupg/secring.gpg").exists()) {
    extra.set("signing.secretKeyRingFile", file("${System.getenv("HOME")}/.gnupg/secring.gpg").absolutePath)
}

signing {
    publishing.publications.configureEach {
        sign(this)
    }
}

publishing.publications.withType<MavenPublication>().configureEach {
    if ("pluginMaven" == name) {
        groupId = project.group as String
        artifactId = pluginExtension.artifactId.orElse(provider { "micronaut-${project.name}" }).get()
        version = project.version as String
    }
    pom {
        name = "Micronaut internal build plugins"
        description = "Micronaut internal build plugins. Not intended to be used in user's projects"
        url = "https://github.com/micronaut-projects/micronaut-build"
        inceptionYear = "2020"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "alvarosanchez"
                name = "Álvaro Sánchez-Mariscal Arnaiz"
            }
            developer {
                id = "melix"
                name = "Cédric Champeau"
            }
        }
        scm {
            connection = "scm:https://github.com/micronaut-projects/micronaut-build.git"
            developerConnection = "scm:git@github.com:micronaut-projects/micronaut-build.git"
            url = "https://github.com/micronaut-projects/micronaut-build"
        }
    }
}

tasks.withType<Sign>().configureEach {
    onlyIf { !project.version.toString().endsWith("-SNAPSHOT") && !project.hasProperty("skipSigning") }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}

val generateVersions = tasks.register<VersionsWriterTask>("generateVersions") {
    className = pluginExtension.versionsFullyQualifiedClassName
    outputDirectory = layout.buildDirectory.dir("generated/versions")
    versions = pluginExtension.versionsMap
}

sourceSets {
    main {
        java.srcDir(generateVersions)
    }
}
