package io.micronaut.build

import groovy.transform.CompileStatic
import io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin
import io.micronaut.build.pom.PomCheckerUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
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
        project.pluginManager.withPlugin("maven-publish") {
            PomCheckerUtils.registerPomChecker("checkPom", project, project.extensions.findByType(PublishingExtension)) {
                it.suppressions.convention(project.extensions.getByType(MicronautBuildExtension).bomSuppressions)
            }
        }
    }

    private static void configureJUnit(Project project) {
        project.tasks.withType(Test).configureEach { Test test ->
            test.useJUnitPlatform()
        }
    }

    static void assertSettingsPluginApplied(Project project) {
        if (project.name == "gradle-kotlin-dsl-accessors") {
            return
        }
        project.gradle.sharedServices.registerIfAbsent(InternalStateCheckingService.NAME, InternalStateCheckingService) { BuildServiceSpec<InternalStateCheckingService.Params> spec ->
            spec.parameters.registeredByProjectPlugin.set(true)
        }.get()
    }
}
