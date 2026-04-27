package io.micronaut.build

import io.micronaut.build.graalvm.GraalVmReachabilityMetadataExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class MicronautParentPluginTest extends Specification {
    def "collects nested published leaf projects for GraalVM reachability metadata"() {
        given:
        Project root = ProjectBuilder.builder().withName("root").build()
        Project aggregator = ProjectBuilder.builder().withName("aggregator").withParent(root).build()
        Project nested = ProjectBuilder.builder().withName("nested-module").withParent(aggregator).build()
        Project direct = ProjectBuilder.builder().withName("direct-module").withParent(root).build()
        [aggregator, nested, direct].each {
            it.group = "io.micronaut.test"
            it.description = "Description for $it.name"
        }
        nested.plugins.apply("maven-publish")
        direct.plugins.apply("maven-publish")

        def extension = root.extensions.create("graalVmReachabilityMetadata", GraalVmReachabilityMetadataExtension)
        extension.excludedProjectNames.convention([] as Set<String>)

        when:
        Map<String, String> modules = collectReachabilityMetadataModules(root, extension)

        then:
        modules == [
            "io.micronaut.test:micronaut-direct-module": "Description for direct-module",
            "io.micronaut.test:micronaut-nested-module": "Description for nested-module"
        ]
    }

    private static Map<String, String> collectReachabilityMetadataModules(Project root, GraalVmReachabilityMetadataExtension extension) {
        def method = MicronautParentPlugin.getDeclaredMethod(
            "collectReachabilityMetadataModules",
            Project,
            GraalVmReachabilityMetadataExtension
        )
        method.accessible = true
        method.invoke(null, root, extension) as Map<String, String>
    }
}
