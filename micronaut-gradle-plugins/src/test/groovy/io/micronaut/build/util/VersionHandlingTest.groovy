package io.micronaut.build.util

import io.micronaut.build.utils.VersionHandling
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class VersionHandlingTest extends Specification {
    def "defaults to Gradle properties when looking for versions"() {
        def project = ProjectBuilder.builder().build()
        project.ext.micronautVersion = "1.0.0"
        project.ext.micronautDocsVersion = "1.5.0"

        when:
        def version = VersionHandling.versionProviderOrDefault(project, alias, '').get()

        then:
        version == expectedVersion

        where:
        alias            | expectedVersion
        "micronaut"      | "1.0.0"
        "micronaut-docs" | "1.5.0"
        "unknown"        | ""
    }
}
