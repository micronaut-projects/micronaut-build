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
        ex.message.contains("This indicates a potentially breaking change in the published version catalog.")
        ex.message.contains("prefer releasing it in a new major version to follow semantic versioning.")
        ex.message.contains('''micronautBom {
    suppressions {
        acceptedVersionRegressions.add("spring")
        acceptedVersionRegressions.add("springboot")
        acceptedLibraryRegressions.add("commons-dbcp")
    }
}''')
        def reportText = report.get().asFile.text
        reportText == '''The following versions were present in the baseline version but missing from this catalog:
  - spring
  - springboot
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
        ex.message.contains("The release cannot proceed without an explicit compatibility decision.")
        ex.message.contains('''micronautBom {
    suppressions {
        acceptedVersionRegressions.add("springboot")
    }
}''')
        def reportText = report.get().asFile.text
        reportText == '''The following versions were present in the baseline version but missing from this catalog:
  - springboot
'''

    }


    static File resource(String path) {
        new File(VersionCatalogCompatibilityCheckTest.getResource(path).toURI())
    }
}
