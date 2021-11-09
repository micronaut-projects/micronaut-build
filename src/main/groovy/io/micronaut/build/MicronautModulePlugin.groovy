package io.micronaut.build

import groovy.transform.CompileStatic
import io.micronaut.build.docs.ConfigurationPropertiesPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
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
            add("annotationProcessor", "io.micronaut.docs:micronaut-docs-asciidoc-config-props:${project.findProperty('micronautDocsVersion')}")

            add("api", "io.micronaut:micronaut-inject")

            add("testImplementation", "org.spockframework:spock-core:${project.findProperty('spockVersion')}") { ExternalModuleDependency d ->
                d.exclude module: 'groovy-all'
            }
            add("testImplementation", "io.micronaut.test:micronaut-test-spock:${project.findProperty('micronautTestVersion')}")
        }
    }
}
