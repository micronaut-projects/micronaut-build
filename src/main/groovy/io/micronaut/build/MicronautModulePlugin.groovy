package io.micronaut.build

import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault

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

    private static void configureStandardDependencies(Project project) {
        var micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        var deps = project.dependencies
        deps.with {
            add("annotationProcessor", "io.micronaut:micronaut-inject-java")
            addProvider("annotationProcessor", versionProviderOrDefault(project, 'micronaut-docs', '').map { "io.micronaut.docs:micronaut-docs-asciidoc-config-props:$it" })
            add("api", "io.micronaut:micronaut-inject")
        }
        project.configurations.getByName("testImplementation").dependencies.addAllLater(
                micronautBuild.testFramework.map {
                    if (it == TestFramework.SPOCK) {
                        return List.of(
                                deps.create(versionProviderOrDefault(project, 'spock', '').map { "org.spockframework:spock-core:${it}" }.get()),
                                deps.create(versionProviderOrDefault(project, 'micronaut-test', '').map { "io.micronaut.test:micronaut-test-spock:${it}" }.get())
                        )
                    } else if (it == TestFramework.JUNIT5) {
                        return List.of(
                                deps.create(versionProviderOrDefault(project, 'junit5', 'unknown').map { "org.junit.jupiter:junit-jupiter-api:${it}" }.get()),
                                deps.create(versionProviderOrDefault(project, 'micronaut-test', '').map { "io.micronaut.test:micronaut-test-junit5:${it}" }.get())
                        )
                    } else {
                        throw new GradleException("Unsupported test framework: $it")
                    }
                }
        )
        project.configurations.getByName("testRuntimeOnly").dependencies.addAllLater(
                micronautBuild.testFramework.map {
                    if (it == TestFramework.SPOCK) {
                        return List.<Dependency>of()
                    } else if (it == TestFramework.JUNIT5) {
                        return List.of(
                                deps.create(versionProviderOrDefault(project, 'junit-platform-launcher', '1.12.0').map { "org.junit.platform:junit-platform-launcher:${it}" }.get()),
                                deps.create(versionProviderOrDefault(project, 'junit5', 'unknown').map { "org.junit.jupiter:junit-jupiter-engine:${it}" }.get()),
                        )
                    } else {
                        throw new GradleException("Unsupported test framework: $it")
                    }
                }
        )
    }
}
