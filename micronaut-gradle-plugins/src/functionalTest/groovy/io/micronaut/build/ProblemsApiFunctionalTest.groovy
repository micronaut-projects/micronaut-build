package io.micronaut.build

import java.nio.file.Files

class ProblemsApiFunctionalTest extends AbstractFunctionalTest {

    void "reports Micronaut version mismatch as a Gradle problem"() {
        given:
        withSample("test-micronaut-module")

        file("subproject1/build.gradle") << """
            dependencies {
                implementation("io.micronaut:micronaut-core:4.8.4")
            }
        """

        when:
        fails 'compileJava'

        then:
        errorOutputContains "Micronaut version mismatch: project declares 4.6.3 but resolved version is 4.8.4. You probably have a dependency which triggered an upgrade of micronaut-core. In order to determine where it comes from, you can run ./gradlew --dependencyInsight --configuration compileClasspath --dependency io.micronaut:micronaut-core"
        problemsReportContains("micronaut-version-mismatch")
        problemsReportContains("Micronaut Build validation")
    }

    void "reports generated Asciidoc output validation failure as a Gradle problem"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            rootProject.name = "asciidoc-validation-problems"
        """
        buildFile << """
            plugins {
                id "io.micronaut.build.internal.docs"
            }

            tasks.register("validateBrokenDocs", io.micronaut.build.docs.ValidateAsciidocOutputTask) {
                inputDirectory = layout.projectDirectory.dir("broken-docs")
                report = layout.buildDirectory.file("reports/broken-docs.txt")
                failOnError = true
            }
        """
        file("broken-docs/index.html").text = "<html><body>Unresolved directive in index.adoc - include::missing.adoc[]</body></html>"

        when:
        fails 'validateBrokenDocs'

        then:
        errorOutputContains "Validation of generated asciidoctor files failed. See the report at"
        problemsReportContains("asciidoc-output-validation-failed")
        problemsReportContains("Micronaut Build validation")
    }

    private void problemsReportContains(String text) {
        def report = testDirectory.resolve("build/reports/problems/problems-report.html")
        assert Files.exists(report): "Expected Gradle Problems report at $report"
        assert report.text.contains(text): "Expected Gradle Problems report to contain '$text'"
    }
}
