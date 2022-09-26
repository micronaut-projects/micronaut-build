package io.micronaut.build

import groovy.transform.CompileStatic
import io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildServiceSpec
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
        project.pluginManager.apply(MicronautBinaryCompatibilityPlugin)
        configureJUnit(project)
        assertSettingsPluginApplied(project)
    }

    private static void configureJUnit(Project project) {
        project.tasks.withType(Test).configureEach { Test test ->
            test.useJUnitPlatform()
        }
    }

    static void assertSettingsPluginApplied(Project project) {
        project.gradle.sharedServices.registerIfAbsent(InternalStateCheckingService.NAME, InternalStateCheckingService) { BuildServiceSpec<InternalStateCheckingService.Params> spec ->
            spec.parameters.registeredByProjectPlugin.set(true)
        }.get()
    }
}
