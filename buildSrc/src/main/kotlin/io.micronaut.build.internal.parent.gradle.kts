plugins {
    base
    id("io.github.gradle-nexus.publish-plugin")
}

version = project.extra.get("projectVersion") as String
group = "io.micronaut.build.internal"

val ossUser = providers.environmentVariableOrSystemProperty("SONATYPE_USERNAME", "sonatypeOssUsername").orElse("")
val ossPass = providers.environmentVariableOrSystemProperty("SONATYPE_PASSWORD", "sonatypeOssPassword").orElse("")

nexusPublishing {
    repositories {
        sonatype {
            username = ossUser
            password = ossPass
            nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
            snapshotRepositoryUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}

val graalVmReachabilityMetadataVersion = providers.gradleProperty("graalVmReachabilityMetadataVersion")
    .orElse(provider { version.toString().removePrefix("v") })
val graalVmReachabilityMetadataOutput = layout.buildDirectory.file("graalvm-reachability-metadata/library-and-framework-list.json")
val graalVmReachabilityMetadataTestsLocations = providers.gradleProperty("graalVmReachabilityMetadataTestsLocations")
    .map { locations ->
        locations.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
    }
    .orElse(emptyList())

val generateGraalVmReachabilityMetadata by tasks.registering {
    description = "Generates Micronaut Build entries for GraalVM reachability metadata's library-and-framework list."
    group = "release"
    val moduleDescriptions = provider {
        subprojects
            .filter { project ->
                !project.name.contains("bom") &&
                    !project.name.startsWith("test-suite") &&
                    project.subprojects.isEmpty() &&
                    project.plugins.hasPlugin("maven-publish")
            }
            .associate { project ->
                "${project.group}:${moduleNameOf(project.name)}" to (project.description ?: moduleNameOf(project.name))
            }
            .toSortedMap()
    }
    inputs.property("minimumVersion", graalVmReachabilityMetadataVersion)
    inputs.property("moduleDescriptions", moduleDescriptions)
    inputs.property("testsLocations", graalVmReachabilityMetadataTestsLocations)
    outputs.file(graalVmReachabilityMetadataOutput)

    doLast {
        val outputFile = graalVmReachabilityMetadataOutput.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            renderGraalVmReachabilityMetadataEntries(
                moduleDescriptions.get(),
                graalVmReachabilityMetadataVersion.get(),
                graalVmReachabilityMetadataTestsLocations.get()
            ),
            Charsets.UTF_8
        )
    }
}

tasks.register("verifyGraalVmReachabilityMetadata") {
    description = "Verifies Micronaut Build's generated GraalVM reachability metadata evidence locations."
    group = "verification"
    dependsOn(generateGraalVmReachabilityMetadata)
    inputs.file(graalVmReachabilityMetadataOutput)

    doLast {
        val outputText = graalVmReachabilityMetadataOutput.get().asFile.readText(Charsets.UTF_8)
        val legacyGraalVmWorkflowUrl = "https://github.com/micronaut-projects/micronaut-build/actions/workflows/graalvm.yml"
        if (outputText.contains(legacyGraalVmWorkflowUrl) && !file(".github/workflows/graalvm.yml").isFile) {
            throw GradleException("Generated GraalVM metadata points to missing workflow .github/workflows/graalvm.yml")
        }
        if (graalVmReachabilityMetadataTestsLocations.get().isEmpty() && !outputText.contains("\"tests_locations\": []")) {
            throw GradleException("Generated GraalVM metadata must omit Micronaut Build test evidence until a valid native-test workflow exists")
        }
    }
}

tasks.named("check") {
    dependsOn("verifyGraalVmReachabilityMetadata")
}

fun renderGraalVmReachabilityMetadataEntries(
    moduleDescriptions: Map<String, String>,
    minimumVersion: String,
    testsLocations: List<String>
): String {
    return buildString {
        append("[\n")
        moduleDescriptions.entries.forEachIndexed { index, entry ->
            append("  {\n")
            append("    \"artifact\": \"").append(escapeJson(entry.key)).append("\",\n")
            append("    \"description\": \"").append(escapeJson(entry.value)).append("\",\n")
            append("    \"details\": [\n")
            append("      {\n")
            append("        \"minimum_version\": \"").append(escapeJson(minimumVersion)).append("\",\n")
            append("        \"metadata_locations\": [],\n")
            append("        \"tests_locations\": ").append(renderJsonStringArray(testsLocations)).append(",\n")
            append("        \"test_level\": \"community-tested\"\n")
            append("      }\n")
            append("    ]\n")
            append("  }")
            if (index + 1 < moduleDescriptions.size) {
                append(",")
            }
            append("\n")
        }
        append("]\n")
    }
}

fun renderJsonStringArray(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") {
    "\"${escapeJson(it)}\""
}

fun moduleNameOf(projectName: String): String =
    if (projectName.startsWith("micronaut-")) projectName else "micronaut-$projectName"

fun escapeJson(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char < ' ') {
                append("\\u").append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
}
