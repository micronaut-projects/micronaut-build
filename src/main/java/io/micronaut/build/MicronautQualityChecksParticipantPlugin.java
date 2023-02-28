package io.micronaut.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.sonarqube.gradle.SonarExtension;
import org.sonarqube.gradle.SonarTask;

import java.io.File;

public class MicronautQualityChecksParticipantPlugin implements Plugin<Project> {

    public static final String MICRONAUT_JACOCO_PROPERTY = "micronaut.jacoco.enabled";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(MicronautBuildExtensionPlugin.class);
        final MicronautBuildExtension micronautBuild = project.getExtensions().findByType(MicronautBuildExtension.class);

        configureCheckstyle(project, micronautBuild);
        configureSonar(project);
        configureJacoco(project, micronautBuild);
    }

    private void configureCheckstyle(final Project project, final MicronautBuildExtension micronautBuildExtension) {
        project.getPluginManager().apply(CheckstylePlugin.class);
        project.afterEvaluate(p -> {
            final CheckstyleExtension checkstyle = p.getExtensions().findByType(CheckstyleExtension.class);
            if (checkstyle != null) {
                checkstyle.setConfigFile(p.getRootProject().getLayout().getProjectDirectory().file("config/checkstyle/checkstyle.xml").getAsFile());
                checkstyle.setToolVersion(micronautBuildExtension.getCheckstyleVersion().get());

                // Per submodule
                checkstyle.setMaxErrors(1);
                checkstyle.setMaxWarnings(10);

                checkstyle.setShowViolations(true);
            }

            p.getTasks().named("checkstyleTest").configure(task -> task.setEnabled(false));
            TaskProvider<Task> checkstyleMain = p.getTasks().named("checkstyleMain");
            checkstyleMain.configure(task -> {
                p.getPluginManager().withPlugin("com.diffplug.spotless", unused ->
                        task.dependsOn("spotlessCheck")
                );
            });
            project.getRootProject().getPluginManager().withPlugin("org.sonarqube", sq -> {
                project.getRootProject().getTasks().withType(SonarTask.class).configureEach(t -> t.dependsOn(checkstyleMain));
            });
        });
    }

    private void configureSonar(final Project project) {
        if (System.getenv("SONAR_TOKEN") != null) {
            project.getRootProject().getPluginManager().withPlugin("org.sonarqube", p -> {
                SonarExtension sonarQubeExtension = project.getExtensions().findByType(SonarExtension.class);
                if (sonarQubeExtension != null) {
                    project.getPluginManager().withPlugin("checkstyle", unused -> {
                        // Because sonar doesn't support the lazy APIs, we can't use
                        // tasks.withType(Checkstyle) to property identify the report,
                        // so any change to this property by a build script will lead
                        // to wrong reports.
                        // Alternatively we could eagerly resolve the checkstyle task,
                        // but we don't want to do this because it makes the build slower
                        sonarQubeExtension.properties(props -> {
                            File checkstyleReport = project.getLayout().getBuildDirectory().file("reports/checkstyle/main.xml").get().getAsFile();
                            props.property("sonar.java.checkstyle.reportPaths", checkstyleReport.getAbsolutePath());
                        });
                    });
                } else {
                    project.getLogger().warn("Could not find the sonarqube extension for project " + project.getName());
                }
            });
        }
    }


    private void configureJacoco(final Project project, MicronautBuildExtension micronautBuild) {
        project.getPluginManager().withPlugin("java", p -> project.getPluginManager().apply(JacocoPlugin.class));
        project.afterEvaluate(unused -> {
            // AfterEvaluate because the jacoco task extension is not registered
            // when our configureEach is called
            project.getTasks().withType(Test.class).configureEach(t -> {
                JacocoTaskExtension jacocoTaskExtension = t.getExtensions().findByType(JacocoTaskExtension.class);
                if (jacocoTaskExtension != null) {
                    ProviderFactory providers = project.getProviders();
                    jacocoTaskExtension.setEnabled(
                            micronautBuild.getEnvironment()
                                    .isGithubAction()
                                    .flatMap(isCi -> {
                                        if (Boolean.TRUE.equals(isCi)) {
                                            return providers.provider(() -> true);
                                        }
                                        return providers.gradleProperty(MICRONAUT_JACOCO_PROPERTY)
                                                .orElse(providers.systemProperty(MICRONAUT_JACOCO_PROPERTY))
                                                .map(Boolean::parseBoolean);
                                    })
                                    .getOrElse(false)
                    );
                }
            });
        });
    }
}
