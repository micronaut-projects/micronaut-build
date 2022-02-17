package io.micronaut.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.sonarqube.gradle.SonarQubeExtension;

import java.io.File;

public class MicronautQualityChecksParticipantPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(MicronautBuildExtensionPlugin.class);
        final MicronautBuildExtension micronautBuild = project.getExtensions().findByType(MicronautBuildExtension.class);

        configureCheckstyle(project, micronautBuild);
        configureSonar(project);
        configureJacoco(project);
    }

    private void configureCheckstyle(final Project project, final MicronautBuildExtension micronautBuildExtension) {
        project.afterEvaluate(p -> {
            p.getPluginManager().apply(CheckstylePlugin.class);
            final CheckstyleExtension checkstyle = p.getExtensions().findByType(CheckstyleExtension.class);
            if (checkstyle != null) {
                checkstyle.setConfigFile(p.file("${rootDir}/config/checkstyle/checkstyle.xml"));
                checkstyle.setToolVersion(micronautBuildExtension.getCheckstyleVersion().get());

                // Per submodule
                checkstyle.setMaxErrors(1);
                checkstyle.setMaxWarnings(10);

                checkstyle.setShowViolations(true);
            }

            p.getTasks().named("checkstyleTest").configure(task -> task.setEnabled(false));
            p.getTasks().named("checkstyleMain").configure(task -> {
                task.dependsOn("spotlessCheck");
                project.getRootProject().getTasks().named("sonarqube").configure(t -> t.dependsOn(task));
            });

        });
    }

    private void configureSonar(final Project project) {
        if (System.getenv("SONAR_TOKEN") != null) {
            project.getRootProject().getPluginManager().withPlugin("org.sonarqube", p -> {
                final SonarQubeExtension sonarQubeExtension = project.getExtensions().findByType(SonarQubeExtension.class);
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


    private void configureJacoco(final Project project) {
        project.getPluginManager().withPlugin("java", p -> project.getPluginManager().apply(JacocoPlugin.class));
    }
}
