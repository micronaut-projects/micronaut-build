package io.micronaut.build

import groovy.transform.CompileStatic
import io.micronaut.build.problems.MicronautBuildProblems
import org.gradle.api.Action
import io.micronaut.build.utils.DefaultVersions
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Problems

import javax.inject.Inject

import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault

/**
 * Configures a project as a typical Micronaut module project,
 * which implies that it's a library aimed at being processed
 * by Micronaut Annotation Processors.
 */
@CompileStatic
class MicronautModulePlugin implements Plugin<Project> {
    private final Problems problems

    @Inject
    MicronautModulePlugin(Problems problems) {
        this.problems = problems
    }

    @Override
    void apply(Project project) {
        project.pluginManager.apply(MicronautBaseModulePlugin)
        configureStandardDependencies(project)
    }

    private void configureStandardDependencies(Project project) {
        var micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        var deps = project.dependencies
        deps.with {
            add("annotationProcessor", "io.micronaut:micronaut-inject-java")
            addProvider("annotationProcessor", versionProviderOrDefault(project, 'micronaut-docs', DefaultVersions.MICRONAUT_DOCS_VERSION).map { "io.micronaut.docs:micronaut-docs-asciidoc-config-props:$it" })
            add("api", "io.micronaut:micronaut-inject")
        }
        project.configurations.getByName("testImplementation").dependencies.addAllLater(
                micronautBuild.testFramework.map {
                    if (it == TestFramework.SPOCK) {
                        return List.of(
                                deps.create(versionProviderOrDefault(project, 'spock', DefaultVersions.SPOCK_VERSION).map { "org.spockframework:spock-core:${it}" }.get()),
                                deps.create(versionProviderOrDefault(project, 'micronaut-test', DefaultVersions.MICRONAUT_TEST_VERSION).map { "io.micronaut.test:micronaut-test-spock:${it}" }.get())
                        )
                    } else if (it == TestFramework.JUNIT6) {
                        return List.of(
                                deps.create(versionProviderOrDefault(project, 'junit6', DefaultVersions.JUNIT6_VERSION).map { "org.junit.jupiter:junit-jupiter-api:${it}" }.get()),
                                deps.create(versionProviderOrDefault(project, 'micronaut-test', DefaultVersions.MICRONAUT_TEST_VERSION).map { "io.micronaut.test:micronaut-test-junit5:${it}" }.get())
                        )
                    } else {
                        return unsupportedTestFramework(it)
                    }
                }
        )
        project.configurations.getByName("testRuntimeOnly").dependencies.addAllLater(
                micronautBuild.testFramework.map {
                    if (it == TestFramework.SPOCK) {
                        return List.<Dependency>of()
                    } else if (it == TestFramework.JUNIT6) {
                        return List.of(
                                deps.create(versionProviderOrDefault(project, 'junit6', DefaultVersions.JUNIT6_VERSION).map { "org.junit.platform:junit-platform-launcher:${it}" }.get()),
                                deps.create(versionProviderOrDefault(project, 'junit6', DefaultVersions.JUNIT6_VERSION).map { "org.junit.jupiter:junit-jupiter-engine:${it}" }.get()),
                        )
                    } else {
                        return unsupportedTestFramework(it)
                    }
                }
        )
    }

    private List<Dependency> unsupportedTestFramework(TestFramework testFramework) {
        String message = "Unsupported test framework: $testFramework"
        throw MicronautBuildProblems.throwing(problems, new GradleException(message), MicronautBuildProblems.UNSUPPORTED_TEST_FRAMEWORK, {
            ProblemSpec spec ->
                spec.contextualLabel(message)
                        .details("The Micronaut Build module plugin only supports ${TestFramework.SPOCK} and ${TestFramework.JUNIT6} test framework defaults.")
                        .solution("Configure micronautBuild.testFramework to ${TestFramework.SPOCK} or ${TestFramework.JUNIT6}.")
        } as Action<? super ProblemSpec>)
    }
}
