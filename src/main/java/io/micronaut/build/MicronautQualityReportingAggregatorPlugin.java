package io.micronaut.build;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport;
import org.gradle.testing.jacoco.plugins.JacocoReportAggregationPlugin;
import org.sonarqube.gradle.SonarExtension;
import org.sonarqube.gradle.SonarQubePlugin;
import org.sonarqube.gradle.SonarTask;

@SuppressWarnings("UnstableApiUsage")
public class MicronautQualityReportingAggregatorPlugin implements Plugin<Project> {

    public static final String COVERAGE_REPORT_TASK_NAME = "testCodeCoverageReport";

    @Override
    public void apply(final Project rootProject) {
        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new GradleException("The Quality Checks Aggregator plugin must only be applied to the root project");
        }

        configureSonar(rootProject);
        configureJacoco(rootProject);
    }


    private void configureSonar(final Project rootProject) {
        if (System.getenv("SONAR_TOKEN") != null) {
            rootProject.getPluginManager().apply(SonarQubePlugin.class);
            final SonarExtension sonarQubeExtension = rootProject.getExtensions().findByType(SonarExtension.class);
            if (sonarQubeExtension != null) {
                sonarQubeExtension.properties(p -> {
                    final String githubSlug = (String) rootProject.findProperty("githubSlug");
                    if (githubSlug != null) {
                        p.property("sonar.projectKey", githubSlug.replaceAll("/", "_"));
                        p.property("sonar.organization", "micronaut-projects");
                        p.property("sonar.host.url", "https://sonarcloud.io");
                        p.property("sonar.java.source", "8");
                        p.property("sonar.verbose", "true");

                        // Jacoco integration
                        final String jacocoReportPath = rootProject.getBuildDir().toPath()
                            .resolve("reports/jacoco/" + COVERAGE_REPORT_TASK_NAME + "/" + COVERAGE_REPORT_TASK_NAME + ".xml")
                            .toFile().getAbsolutePath();
                        p.property("sonar.coverage.jacoco.xmlReportPaths", jacocoReportPath);
                    }
                });

                rootProject.getTasks().withType(SonarTask.class).configureEach(t -> t.dependsOn(COVERAGE_REPORT_TASK_NAME));
            } else {
                rootProject.getLogger().warn("Could not find the sonarqube extension");
            }
        }
    }

    private void configureJacoco(final Project rootProject) {
        rootProject.getPluginManager().apply(BasePlugin.class);
        rootProject.getPluginManager().apply(JacocoReportAggregationPlugin.class);

        ReportingExtension reporting = rootProject.getExtensions().getByType(ReportingExtension.class);
        reporting.getReports().create(COVERAGE_REPORT_TASK_NAME, JacocoCoverageReport.class,
                r -> r.getTestType().set(TestSuiteType.UNIT_TEST));

        final Configuration jacocoAggregation = rootProject.getConfigurations().getByName("jacocoAggregation");
        rootProject.getSubprojects().forEach(subproject -> {
            rootProject.evaluationDependsOn(subproject.getPath());
            subproject.getPlugins().withType(MicronautQualityChecksParticipantPlugin.class, plugin -> {
                jacocoAggregation.getDependencies().add(
                        rootProject.getDependencies().create(subproject)
                );
            });
        });
    }

}
