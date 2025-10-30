package io.micronaut.build.compat


import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.nio.file.Files

class VersionCatalogCompatibilityCheckTest extends Specification {
    def "compares version catalogs"() {
        def project = ProjectBuilder.builder().build()
        def report = project.layout.buildDirectory.file("reports/report.txt")
        Files.createDirectories(report.get().asFile.parentFile.toPath())
        def task = project.tasks.create("compat", VersionCatalogCompatibilityCheck) {
            baseline.set(resource("/baseline.toml"))
            current.set(resource("/current.toml"))
            reportFile.set(report)
        }

        when:
        task.checkCompatibility()

        then:
        RuntimeException ex = thrown()
        ex.message.startsWith("Version catalogs are not compatible:")
        def reportText = report.get().asFile.text
        reportText == '''The following versions were present in the baseline version but missing from this catalog:
  - springboot
  - spring
The following libraries were present in the baseline version but missing from this catalog:
  - commons-dbcp
'''
    }

    def "can accept regressions"() {
        def project = ProjectBuilder.builder().build()
        def report = project.layout.buildDirectory.file("reports/report.txt")
        Files.createDirectories(report.get().asFile.parentFile.toPath())
        def task = project.tasks.create("compat", VersionCatalogCompatibilityCheck) {
            baseline.set(resource("/baseline.toml"))
            current.set(resource("/current.toml"))
            reportFile.set(report)
            acceptedVersionRegressions.add("spring")
            acceptedLibraryRegressions.add("commons-dbcp")
        }

        when:
        task.checkCompatibility()

        then:
        RuntimeException ex = thrown()
        ex.message.startsWith("Version catalogs are not compatible:")
        def reportText = report.get().asFile.text
        reportText == '''The following versions were present in the baseline version but missing from this catalog:
  - springboot
'''

    }


    static File resource(String path) {
        new File(VersionCatalogCompatibilityCheckTest.getResource(path).toURI())
    }
}
