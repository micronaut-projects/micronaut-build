package io.micronaut.build;

import io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin;
import io.micronaut.build.info.MicronautModuleInfoPlugin;
import io.micronaut.build.pom.PomCheckerUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.tasks.testing.Test;

/**
 * Configures a project as a typical Micronaut module project:
 *  - with dependency updates plugin
 *  - published on a Maven repository
 *  - with JUnit platform testing
 */
public class MicronautBaseModulePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBuildCommonPlugin.class);
        project.getPluginManager().apply(MicronautDependencyUpdatesPlugin.class);
        project.getPluginManager().apply(MicronautPublishingPlugin.class);
        project.getPluginManager().apply(MicronautBinaryCompatibilityPlugin.class);
        project.getPluginManager().apply(SonatypeConfigurationPlugin.class);
        project.getPluginManager().apply(MicronautModuleInfoPlugin.class);
        project.getPluginManager().apply(MicronautNullAwayPlugin.class);
        configureJUnit(project);
        assertSettingsPluginApplied(project);
        project.getPluginManager().withPlugin("maven-publish", plugin -> {
            PomCheckerUtils.registerPomChecker("checkPom", project, project.getExtensions().findByType(PublishingExtension.class), task -> {
                task.getSuppressions().convention(project.getExtensions().getByType(MicronautBuildExtension.class).getBomSuppressions());
            });
        });
    }

    private static void configureJUnit(Project project) {
        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);
    }

    static void assertSettingsPluginApplied(Project project) {
        if ("gradle-kotlin-dsl-accessors".equals(project.getName())) {
            return;
        }
        project.getGradle().getSharedServices().registerIfAbsent(
            InternalStateCheckingService.NAME,
            InternalStateCheckingService.class,
            spec -> spec.getParameters().getRegisteredByProjectPlugin().set(true)
        ).get();
    }
}
