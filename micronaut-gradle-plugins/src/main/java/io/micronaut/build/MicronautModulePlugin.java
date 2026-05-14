package io.micronaut.build;

import io.micronaut.build.utils.DefaultVersions;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.util.List;

import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault;

/**
 * Configures a project as a typical Micronaut module project,
 * which implies that it's a library aimed at being processed
 * by Micronaut Annotation Processors.
 */
public class MicronautModulePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBaseModulePlugin.class);
        configureStandardDependencies(project);
    }

    private static void configureStandardDependencies(Project project) {
        MicronautBuildExtension micronautBuild = project.getExtensions().getByType(MicronautBuildExtension.class);
        DependencyHandler deps = project.getDependencies();
        deps.add("annotationProcessor", "io.micronaut:micronaut-inject-java");
        deps.addProvider("annotationProcessor", versionProviderOrDefault(project, "micronaut-docs", DefaultVersions.MICRONAUT_DOCS_VERSION)
            .map(version -> "io.micronaut.docs:micronaut-docs-asciidoc-config-props:" + version));
        deps.add("api", "io.micronaut:micronaut-inject");

        project.getConfigurations().getByName("testImplementation").getDependencies().addAllLater(
            micronautBuild.getTestFramework().map(testFramework -> testImplementationDependencies(project, deps, testFramework))
        );
        project.getConfigurations().getByName("testRuntimeOnly").getDependencies().addAllLater(
            micronautBuild.getTestFramework().map(testFramework -> testRuntimeOnlyDependencies(project, deps, testFramework))
        );
    }

    private static List<Dependency> testImplementationDependencies(Project project, DependencyHandler deps, TestFramework testFramework) {
        if (testFramework == TestFramework.SPOCK) {
            return List.of(
                deps.create(versionProviderOrDefault(project, "spock", DefaultVersions.SPOCK_VERSION)
                    .map(version -> "org.spockframework:spock-core:" + version).get()),
                deps.create(versionProviderOrDefault(project, "micronaut-test", DefaultVersions.MICRONAUT_TEST_VERSION)
                    .map(version -> "io.micronaut.test:micronaut-test-spock:" + version).get())
            );
        } else if (testFramework == TestFramework.JUNIT6) {
            return List.of(
                deps.create(versionProviderOrDefault(project, "junit6", DefaultVersions.JUNIT6_VERSION)
                    .map(version -> "org.junit.jupiter:junit-jupiter-api:" + version).get()),
                deps.create(versionProviderOrDefault(project, "micronaut-test", DefaultVersions.MICRONAUT_TEST_VERSION)
                    .map(version -> "io.micronaut.test:micronaut-test-junit5:" + version).get())
            );
        }
        throw new GradleException("Unsupported test framework: " + testFramework);
    }

    private static List<Dependency> testRuntimeOnlyDependencies(Project project, DependencyHandler deps, TestFramework testFramework) {
        if (testFramework == TestFramework.SPOCK) {
            return List.of();
        } else if (testFramework == TestFramework.JUNIT6) {
            return List.of(
                deps.create(versionProviderOrDefault(project, "junit6", DefaultVersions.JUNIT6_VERSION)
                    .map(version -> "org.junit.platform:junit-platform-launcher:" + version).get()),
                deps.create(versionProviderOrDefault(project, "junit6", DefaultVersions.JUNIT6_VERSION)
                    .map(version -> "org.junit.jupiter:junit-jupiter-engine:" + version).get())
            );
        }
        throw new GradleException("Unsupported test framework: " + testFramework);
    }
}
