import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("io.micronaut.build.internal.docs")
}

description = "Docs layout test-suite"

val generatedDocs = layout.buildDirectory.dir("docs")

tasks.register("compileTestJava") {
    group = "verification"
    dependsOn(":verification:compileTestJava")
}

tasks.register("test") {
    group = "verification"
    dependsOn(tasks.named("docs"))
    dependsOn(":verification:test")
}

tasks.named("check") {
    dependsOn(tasks.named("test"))
}

allprojects {
    tasks.withType<PublishToMavenRepository>().configureEach {
        enabled = false
    }
    tasks.withType<PublishToMavenLocal>().configureEach {
        enabled = false
    }
}

tasks.register<Exec>("openDocs") {
    group = "mndocs"
    description = "Generates the docs layout test-suite project docs and opens the guide in the default browser."
    dependsOn(tasks.named("docs"))

    val index = generatedDocs.map { it.file("guide/index.html") }.get().asFile.absolutePath
    val os = System.getProperty("os.name").lowercase()
    when {
        os.contains("mac") -> commandLine("open", index)
        os.contains("windows") -> commandLine("cmd", "/c", "start", "", index)
        else -> commandLine("xdg-open", index)
    }
}
