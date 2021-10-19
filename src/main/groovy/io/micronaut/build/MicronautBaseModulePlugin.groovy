package io.micronaut.build

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.tasks.testing.Test

/**
 * Configures a project as a typical Micronaut module project:
 *  - with dependency updates plugin
 *  - published on a Maven repository
 *  - with JUnit platform testing
 */
@CompileStatic
class MicronautBaseModulePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(MicronautBuildCommonPlugin)
        project.pluginManager.apply(MicronautDependencyUpdatesPlugin)
        project.pluginManager.apply(MicronautPublishingPlugin)
        configureJUnit(project)
    }

    private static void configureJUnit(Project project) {
        project.tasks.withType(Test).configureEach { Test test ->
            test.useJUnitPlatform()
        }
    }

}
