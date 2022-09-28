package io.micronaut.build

import groovy.transform.CompileStatic
import io.micronaut.build.catalogs.MicronautVersionCatalogUpdatePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
@CompileStatic
class MicronautDependencyUpdatesPlugin implements Plugin<Project> {


    @Override
    void apply(Project project) {
        project.pluginManager.apply(MicronautBuildExtensionPlugin)
        if (project.rootProject.file("gradle/libs.versions.toml").exists()) {
            if (project == project.rootProject) {
                project.pluginManager.apply(MicronautVersionCatalogUpdatePlugin)
            }
            return
        }

        MicronautBuildExtension micronautBuildExtension = project.extensions.getByType(MicronautBuildExtension)

        project.configurations.all { Configuration cfg ->
            if (micronautBuildExtension.resolutionStrategy) {
                cfg.resolutionStrategy(micronautBuildExtension.resolutionStrategy)
            }
        }

        project.with {
            tasks.register("dependencyUpdates", DeprecatedTask) {
                it.message.set("The dependencyUpdates task is scheduled for removal")
                it.replacement.set("Renovatebot")
            }

            tasks.register("useLatestVersions", DeprecatedTask) {
                it.message.set("The useLatestVersions task is scheduled for removal")
                it.replacement.set("Renovatebot")
            }
        }
    }
}
