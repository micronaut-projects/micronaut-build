package io.micronaut.build;

import static io.micronaut.build.MicronautQualityChecksAggregatorPlugin.COVERAGE_REPORT_TASK_NAME;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.sonarqube.gradle.SonarQubeExtension;

public class MicronautQualityChecksParticipantPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
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
                project.getRootProject().getTasks().named(COVERAGE_REPORT_TASK_NAME).configure(t -> t.dependsOn(task));
            });

        });
    }

    private void configureSonar(final Project project) {
        if (System.getenv("SONAR_TOKEN") != null) {
            project.getRootProject().getPluginManager().withPlugin("org.sonarqube", p -> {
                final SonarQubeExtension sonarQubeExtension = project.getExtensions().findByType(SonarQubeExtension.class);
                if (sonarQubeExtension != null) {
                    final File checkstyleReportPath = project.getBuildDir().toPath().resolve("reports/checkstyle/main.xml").toFile();
                    if (checkstyleReportPath.exists()) {
                        sonarQubeExtension.properties(props -> {
                            props.property("sonar.java.checkstyle.reportPaths", checkstyleReportPath.getAbsolutePath());
                        });
                    }
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
