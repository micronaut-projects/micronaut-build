package io.micronaut.build

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

import static io.micronaut.build.util.VersionHandling.versionProviderOrDefault
/**
 * Configures a project as a typical Micronaut module project,
 * which implies that it's a library aimed at being processed
 * by Micronaut Annotation Processors.
 */
@CompileStatic
class MicronautModulePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply(MicronautBaseModulePlugin)
        configureStandardDependencies(project)
    }

    private static Dependency configureStandardDependencies(Project project) {
        project.dependencies.with {
            add("annotationProcessor", "io.micronaut:micronaut-inject-java")
            addProvider("annotationProcessor", versionProviderOrDefault(project, 'micronaut-docs', '').map { "io.micronaut.docs:micronaut-docs-asciidoc-config-props:$it"})
            add("api", "io.micronaut:micronaut-inject")

            addProvider("testImplementation", versionProviderOrDefault(project, 'spock', '').map { "org.spockframework:spock-core:${it}" })
            addProvider("testImplementation", versionProviderOrDefault(project, 'micronaut-test', '').map { "io.micronaut.test:micronaut-test-spock:${it}"})
            return
        }
    }
}
