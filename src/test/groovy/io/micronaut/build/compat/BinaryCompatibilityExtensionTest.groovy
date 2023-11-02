package io.micronaut.build.compat

import io.micronaut.build.MicronautPublishingPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BinaryCompatibilityExtensionTest extends Specification {
    def "can disable binary compatibility for specific releases"() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(JavaLibraryPlugin)
        project.pluginManager.apply(MicronautPublishingPlugin)
        project.pluginManager.apply(MicronautBinaryCompatibilityPlugin)

        when:
        project.version = projectVersion
        project.micronautBuild.binaryCompatibility.enabledAfter testVersion

        then:
        project.micronautBuild.binaryCompatibility.enabled.get() == enabled

        where:
        projectVersion | testVersion | enabled
        '1.0.0'        | '0.9.0'     | true
        '1.0.0'        | '1.1.0'     | false
        '1.0.0'        | '1.1.0'     | false
        '1.0.1'        | '1.0.0'     | true
        '1.1.0'        | '1.0.0'     | true
        '2.0.0'        | '1.0.0'     | true

    }
}
