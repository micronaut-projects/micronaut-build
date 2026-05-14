package io.micronaut.build;

import groovy.lang.Closure;
import io.micronaut.build.catalogs.MicronautVersionCatalogUpdatePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolutionStrategy;

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
public class MicronautDependencyUpdatesPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBuildExtensionPlugin.class);
        if (project.getRootProject().file("gradle/libs.versions.toml").exists()) {
            if (project == project.getRootProject()) {
                project.getPluginManager().apply(MicronautVersionCatalogUpdatePlugin.class);
            }
            return;
        }

        MicronautBuildExtension micronautBuildExtension = project.getExtensions().getByType(MicronautBuildExtension.class);

        project.getConfigurations().all(configuration -> {
            Closure<?> resolutionStrategy = micronautBuildExtension.getResolutionStrategy();
            if (resolutionStrategy != null) {
                configuration.resolutionStrategy(strategy -> configureResolutionStrategy(resolutionStrategy, strategy));
            }
        });

        project.getTasks().register("dependencyUpdates", DeprecatedTask.class, task -> {
            task.getMessage().set("The dependencyUpdates task is scheduled for removal");
            task.getReplacement().set("Renovatebot");
        });

        project.getTasks().register("useLatestVersions", DeprecatedTask.class, task -> {
            task.getMessage().set("The useLatestVersions task is scheduled for removal");
            task.getReplacement().set("Renovatebot");
        });
    }

    private static void configureResolutionStrategy(Closure<?> closure, ResolutionStrategy strategy) {
        closure.setDelegate(strategy);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(strategy);
    }
}
