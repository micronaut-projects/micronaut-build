package io.micronaut.build.graalvm

import io.micronaut.build.MicronautBuildExtension
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class NativeImageSupportPluginTest extends Specification {
    def "configures an extension"() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(NativeImageSupportPlugin)
        def micronautBuildExtension = project.extensions.findByType(MicronautBuildExtension)

        when:
        def nativeImagePropsExtension = micronautBuildExtension.extensions.findByType(NativeImagePropertiesExtension)

        then:
        nativeImagePropsExtension.enabled.get() == false

        when:
        def task = project.tasks.named(NativeImageSupportPlugin.GENERATE_NATIVE_IMAGE_PROPERTIES_TASK_NAME, NativePropertiesWriter)

        then:
        task.present
    }
}
