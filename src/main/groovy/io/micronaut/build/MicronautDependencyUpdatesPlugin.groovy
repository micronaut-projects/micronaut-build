package io.micronaut.build

import io.micronaut.build.catalogs.MicronautVersionCatalogUpdatePlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.quality.Checkstyle

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautDependencyUpdatesPlugin implements Plugin<Project> {


    public static final String GRADLE_VERSIONS_PLUGIN = "com.github.ben-manes.versions"
    public static final String USE_LATEST_VERSIONS_PLUGIN = "se.patrikerdes.use-latest-versions"

    @Override
    void apply(Project project) {
        project.pluginManager.apply(MicronautBuildExtensionPlugin)
        if (project.rootProject.file("gradle/libs.versions.toml").exists()) {
            if (project == project.rootProject) {
                project.pluginManager.apply(MicronautVersionCatalogUpdatePlugin)
            }
            return
        }
        project.apply plugin: GRADLE_VERSIONS_PLUGIN
        project.apply plugin: USE_LATEST_VERSIONS_PLUGIN

        MicronautBuildExtension micronautBuildExtension = project.extensions.getByType(MicronautBuildExtension)

        project.configurations.all { Configuration cfg ->
            if (micronautBuildExtension.resolutionStrategy) {
                cfg.resolutionStrategy(micronautBuildExtension.resolutionStrategy)
            }
        }

        project.with {
            tasks.named("dependencyUpdates") {
                onlyIf {
                    gradle.taskGraph.hasTask(":useLatestVersions")
                }
                checkForGradleUpdate = true
                gradleReleaseChannel = "current"
                checkConstraints = true
                revision = "release"
                rejectVersionIf { mod ->
                    mod.candidate.version ==~
                            micronautBuildExtension.dependencyUpdatesPattern ||
                            ['alpha', 'beta', 'milestone', 'rc', 'cr', 'm', 'preview', 'b', 'ea'].any { qualifier ->
                                mod.candidate.version ==~ /(?i).*[.-]$qualifier[.\d-+]*/
                            } ||
                            mod.candidate.group == 'io.micronaut' // managed by the micronaut version
                }

                outputFormatter = { result ->
                    if (!result.outdated.dependencies.isEmpty()) {
                        def upgradeVersions = result.outdated.dependencies
                        if (!upgradeVersions.isEmpty()) {
                            println "\nThe following dependencies have later ${revision} versions:"
                            upgradeVersions.each { dep ->
                                def currentVersion = dep.version
                                println " - ${dep.group}:${dep.name} [${currentVersion} -> ${dep.available[revision]}]"
                                if (dep.projectUrl != null) {
                                    println "     ${dep.projectUrl}"
                                }
                            }
                            throw new GradleException('Abort, there are dependencies to update. Run ./gradlew useLatestVersions to update them in place')
                        }
                    }
                }
            }

            tasks.named("useLatestVersions") {
                updateRootProperties = true
            }

            tasks.withType(Checkstyle).configureEach {
                it.dependsOn('dependencyUpdates')
            }
        }
    }
}
