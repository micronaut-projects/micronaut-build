package io.micronaut.build;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.internal.GradleInternal;

import java.util.concurrent.TimeUnit;

public class MicronautDependencyResolutionConfigurationPlugin implements MicronautPlugin<Project> {

    @Override
    public void apply(Project project) {
        addMavenCentral(project);
        configureDependencySubstitutions(project);
        configureResolutionStrategy(project);
        configureDependencyCaching(project);
    }

    private static void configureResolutionStrategy(Project project) {
        project.getConfigurations().all(conf -> conf.resolutionStrategy(ResolutionStrategy::preferProjectModules));
    }

    private static void configureDependencyCaching(Project project) {
        project.getPlugins().withType(MicronautBuildExtensionPlugin.class, unused -> {
                    MicronautBuildExtension micronautBuildExtension = project.getExtensions().findByType(MicronautBuildExtension.class);
                    // Disable caching of snapshots and dynamic versions when running on GitHub Actions
                    if (Boolean.TRUE.equals(micronautBuildExtension.getEnvironment().isGithubAction().get())) {
                        project.getConfigurations().all(conf -> conf.resolutionStrategy(rs -> {
                            rs.cacheChangingModulesFor(0, TimeUnit.MINUTES);
                            rs.cacheDynamicVersionsFor(0, TimeUnit.MINUTES);
                        }));
                    }
                }
        );
    }

    private void addMavenCentral(Project project) {
        // TODO: Avoid use of internal API
        Settings settings = ((GradleInternal) project.getGradle()).getSettings();
        RepositoriesMode repositoriesMode = settings.getDependencyResolutionManagement().getRepositoriesMode().get();
        if (!repositoriesMode.equals(repositoriesMode.FAIL_ON_PROJECT_REPOS)) {
            project.getRepositories().mavenCentral();
        }

    }

    public static void configureDependencySubstitutions(Project project) {
        project.getGradle().settingsEvaluated(settings -> {
            MicronautBuildSettingsExtension buildSettingsExtension = settings.getExtensions().getByType(MicronautBuildSettingsExtension.class);
            project.getGradle().projectsEvaluated(unused -> project.getConfigurations().all(conf -> conf.getResolutionStrategy().dependencySubstitution(ds ->
                    project.getRootProject().getAllprojects().forEach(p -> {
                        String shortName = p.getName().replaceFirst(MICRONAUT_PROJECT_PREFIX, "");
                        String group = String.valueOf(p.getGroup());
                        if (group.startsWith(MICRONAUT_GROUP_ID) && !shortName.startsWith(TEST_SUITE_PROJECT_PREFIX)) {
                            ComponentSelector releasedModule = ds.module(group + ":" + MICRONAUT_PROJECT_PREFIX + shortName);
                            String projectPath = ":" + buildSettingsExtension.getUseStandardizedProjectNames().map(s -> s ? MICRONAUT_PROJECT_PREFIX : "").get() + shortName;
                            ComponentSelector localVersion = ds.project(projectPath);
                            if (shortName.endsWith(BOM_PROJECT_SUFFIX)) {
                                ds.substitute(ds.platform(releasedModule)).using(ds.platform(localVersion)).because("we want to test with what we're building");
                            } else {
                                ds.substitute(releasedModule).using(localVersion).because("we want to test with what we're building");
                            }
                        }
                    }))
            ));
        });
    }

}
