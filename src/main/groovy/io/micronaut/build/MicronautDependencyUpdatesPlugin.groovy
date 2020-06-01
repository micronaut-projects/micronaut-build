package io.micronaut.build

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautDependencyUpdatesPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.apply plugin: "com.github.ben-manes.versions"
        project.tasks.getByName("check").dependsOn('dependencyUpdates')

        project.afterEvaluate {
            MicronautBuildExtension micronautBuildExtension = project.extensions.getByType(MicronautBuildExtension)

            project.configurations.all { Configuration cfg ->
                if (micronautBuildExtension.resolutionStrategy) {
                    cfg.resolutionStrategy(micronautBuildExtension.resolutionStrategy)
                }
            }

            project.with {
                dependencyUpdates {
                    checkForGradleUpdate = true
                    checkConstraints = true
                    rejectVersionIf { mod ->
                        mod.candidate.version ==~ micronautBuildExtension.dependencyUpdatesPattern ||
                                ["alpha", "beta", "milestone", "preview"].any { mod.candidate.version.toLowerCase(Locale.ENGLISH).contains(it) } ||
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
                                throw new GradleException('Abort, there are dependencies to update.')
                            }
                        }
                    }
                }
            }
        }
    }
}
