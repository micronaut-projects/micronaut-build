package io.micronaut.build

import groovy.transform.CompileStatic
import io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.api.tasks.testing.Test

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configures a project as a typical Micronaut module project:
 *  - with dependency updates plugin
 *  - published on a Maven repository
 *  - with JUnit platform testing
 */
@CompileStatic
class MicronautBaseModulePlugin implements Plugin<Project> {
    private final static AtomicBoolean WARNING_ISSUED = new AtomicBoolean()

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
        def plugin = ((GradleInternal) project.gradle).getSettings().getPlugins().findPlugin("io.micronaut.build.shared.settings")
        if (plugin == null) {
            if (!WARNING_ISSUED.getAndSet(true)) {
                project.gradle.buildFinished {
                    project.logger.warn("""
WARNING!

The internal Micronaut Build plugins have been updated, but the settings plugin hasn't been applied.
You must apply the shared settings plugin by modifying the settings.gradle(.kts) file by adding on the top:

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "<plugin version>"
}

Not doing so will result in a failure when publishing.

""")
                }
            }
        }
    }
}
