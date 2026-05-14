description = "Docs layout test-suite runner"

val gradlew = rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath
val pluginRuntimeClasspath = project(":micronaut-gradle-plugins").configurations.named("runtimeClasspath")
val testSuiteProjectDirs = layout.projectDirectory.dir("projects").asFile
    .listFiles { file -> file.isDirectory && (file.resolve("settings.gradle").isFile || file.resolve("settings.gradle.kts").isFile) }
    ?.sortedBy { it.name }
    .orEmpty()
val docsLayoutProject = layout.projectDirectory.dir("projects/docs-layout")

val cleanProjectTasks = testSuiteProjectDirs.map { projectDir ->
    tasks.register<Exec>("clean${projectDir.name.split('-', '_').joinToString("") { it.replaceFirstChar(Char::uppercase) }}") {
        group = "build"
        description = "Cleans the ${projectDir.name} test-suite project."
        commandLine(
            gradlew,
            "-q",
            "-Dorg.gradle.vfs.watch=false",
            "-p",
            projectDir.absolutePath,
            ":clean"
        )
    }
}

tasks.register<Exec>("docs") {
    group = "mndocs"
    description = "Generates the docs layout test-suite project docs."
    dependsOn(pluginRuntimeClasspath)
    dependsOn(cleanProjectTasks)
    commandLine(
        gradlew,
        "-q",
        "-Dorg.gradle.vfs.watch=false",
        "-p",
        docsLayoutProject.asFile.absolutePath,
        ":docs"
    )
}

tasks.register<Exec>("compileTestJava") {
    group = "verification"
    description = "Compiles the docs layout test-suite Java tests."
    dependsOn(pluginRuntimeClasspath)
    commandLine(
        gradlew,
        "-q",
        "-Dorg.gradle.vfs.watch=false",
        "-p",
        docsLayoutProject.asFile.absolutePath,
        ":compileTestJava"
    )
}

val test by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the docs layout generated output tests."
    dependsOn(pluginRuntimeClasspath)
    dependsOn(cleanProjectTasks)
    commandLine(
        gradlew,
        "-Dorg.gradle.vfs.watch=false",
        "-p",
        docsLayoutProject.asFile.absolutePath,
        ":test"
    )
}

tasks.register("check") {
    group = "verification"
    dependsOn(test)
}

tasks.register("cleanProjects") {
    group = "build"
    description = "Cleans all Gradle projects under test-suite/projects."
    dependsOn(cleanProjectTasks)
}

tasks.register("clean") {
    group = "build"
    description = "Cleans all generated test-suite project outputs."
    dependsOn("cleanProjects")
}

tasks.register<Exec>("openDocs") {
    group = "mndocs"
    description = "Generates the docs layout test-suite project docs and opens the guide in the default browser."
    dependsOn(pluginRuntimeClasspath)
    dependsOn(cleanProjectTasks)
    commandLine(
        gradlew,
        "-q",
        "-Dorg.gradle.vfs.watch=false",
        "-p",
        docsLayoutProject.asFile.absolutePath,
        ":openDocs"
    )
}
