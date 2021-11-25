package io.micronaut.build


import io.micronaut.build.pom.PomChecker
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.BasePlugin

/**
 * This plugin verifies the BOMs which are used in the project
 * version catalog.
 */
abstract class MicronautBomCheckerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(BasePlugin)
        def catalog = project.extensions.findByType(VersionCatalogsExtension).named("libs")
        project.with {
            def checkBoms = tasks.register("checkBom")

            project.tasks.named("check") {
                dependsOn(checkBoms)
            }

            // First we create a task per BOM that we include, in order
            // to check that they exist for the specified version and
            // that their dependencyManagement block only contains expected
            // dependencies
            catalog.dependencyAliases.stream()
                    .filter { it.startsWith("boms.") }
                    .forEach { alias ->
                        String name = alias.split("[.]").collect { it.capitalize() }.join('')
                        def checker = tasks.register("check$name", PomChecker) {
                            repositories.set([repoUrl(project.repositories.findByName("MavenRepo"))])
                            pomCoordinates.set(catalog.findDependency(alias).map {
                                it.map { "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}".toString() }
                            }.get())
                            checkBomContents.set(true)
                            report.set(layout.buildDirectory.file("reports/boms/${alias}.txt"))
                        }
                        checkBoms.configure {
                            it.dependsOn(checker)
                        }
                    }

            // Then we create a task per dependency we include in the BOM
            // to check that it exists
            catalog.dependencyAliases.stream()
                    .filter { it.startsWith("managed.") }
                    .forEach { alias ->
                        String name = alias.split("[.]").collect { it.capitalize() }.join('')
                        def checker = tasks.register("check$name", PomChecker) {
                            repositories.set([repoUrl(project.repositories.findByName("MavenRepo"))] + project.repositories.collect { repoUrl(it) })
                            pomCoordinates.set(catalog.findDependency(alias).map {
                                it.map { "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}".toString() }
                            }.get())
                            checkBomContents.set(false)
                            report.set(layout.buildDirectory.file("reports/dependencies/${alias}.txt"))
                        }
                        checkBoms.configure {
                            it.dependsOn(checker)
                        }
                    }
        }
    }

    static String repoUrl(ArtifactRepository repository) {
        (repository as MavenArtifactRepository).url
    }
}
