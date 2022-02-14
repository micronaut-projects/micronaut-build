package io.micronaut.build;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoReportAggregationPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.sonarqube.gradle.SonarQubeExtension;
import org.sonarqube.gradle.SonarQubePlugin;

import java.util.Collections;

@SuppressWarnings("UnstableApiUsage")
public class MicronautQualityChecksPlugin implements Plugin<Project> {

    public static final String COVERAGE_REPORT_TASK_NAME = "testCodeCoverageReport";
    public static final String TEST_REPORT_TASK_NAME = "jacocoTestReport";

    @Override
    public void apply(final Project rootProject) {
        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new GradleException("The Quality Checks plugin must only be applied to the root project");
        }

        configureSonar(rootProject);
        configureJacoco(rootProject);
        rootProject.getSubprojects().forEach(this::subprojectApply);
    }

    private void subprojectApply(final Project subproject) {
        configureJacocoForSubproject(subproject);
    }

    private void configureSonar(final Project rootProject) {
        rootProject.getPluginManager().apply(SonarQubePlugin.class);
        final SonarQubeExtension sonarQubeExtension = rootProject.getExtensions().findByType(SonarQubeExtension.class);
        final String githubSlug = (String) rootProject.findProperty("githubSlug");
        sonarQubeExtension.properties(p -> {
            p.property("sonar.projectKey", githubSlug.replaceAll("/", "_"));
            p.property("sonar.organization", "micronaut-projects");
            p.property("sonar.host.url", "https://sonarcloud.io");
            p.property("sonar.java.source", "8");
        });
    }

    private void configureJacoco(final Project rootProject) {
        rootProject.getPluginManager().apply(BasePlugin.class);
        rootProject.getPluginManager().apply(JacocoReportAggregationPlugin.class);

        rootProject.getTasks().register(COVERAGE_REPORT_TASK_NAME, JacocoReport.class);

        ReportingExtension reporting = rootProject.getExtensions().getByType(ReportingExtension.class);
        reporting.getReports().register(COVERAGE_REPORT_TASK_NAME, JacocoCoverageReport.class,
                r -> r.getTestType().set(TestSuiteType.UNIT_TEST));
    }

    private void configureJacocoForSubproject(Project subproject) {
        if (subproject.getPlugins().hasPlugin(JavaPlugin.class)) {
            subproject.getPluginManager().apply(JacocoPlugin.class);
        }

        // Do not generate reports for individual projects
        if (!subproject.getTasks().matching(task -> task.getName().equals(TEST_REPORT_TASK_NAME)).isEmpty()) {
            subproject.getTasks().named(TEST_REPORT_TASK_NAME).configure(task -> task.setEnabled(false));
        }

        Project rootProject = subproject.getRootProject();
        Configuration jacocoAggregation = rootProject.getConfigurations().getByName("jacocoAggregation");
        jacocoAggregation.getDependencies().add(
                rootProject.getDependencies().project(Collections.singletonMap("path", subproject.getPath()))
        );
    }
}
