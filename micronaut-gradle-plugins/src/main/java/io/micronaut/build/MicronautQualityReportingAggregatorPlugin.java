package io.micronaut.build;

import io.micronaut.build.sonarlint.SonarLintSupport;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport;
import org.gradle.testing.jacoco.plugins.JacocoReportAggregationPlugin;
import org.gradle.util.GradleVersion;
import org.sonarqube.gradle.SonarExtension;
import org.sonarqube.gradle.SonarProperties;
import org.sonarqube.gradle.SonarQubePlugin;
import org.sonarqube.gradle.SonarTask;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public class MicronautQualityReportingAggregatorPlugin implements Plugin<Project> {

    public static final String COVERAGE_REPORT_TASK_NAME = "testCodeCoverageReport";
    private static final String MULTICRITERIA = "sonar.issue.ignore.multicriteria";
    private static final String TEST_ISSUES_CRITERION = "micronautTestSources";

    @Override
    public void apply(final Project rootProject) {
        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new GradleException("The Quality Checks Aggregator plugin must only be applied to the root project");
        }

        configureSonar(rootProject);
        configureJacoco(rootProject);
        SonarLintSupport.registerExportRules(rootProject);
    }


    private void configureSonar(final Project rootProject) {
        if (System.getenv("SONAR_TOKEN") != null) {
            rootProject.getPluginManager().apply(SonarQubePlugin.class);
            SonarExtension sonarQubeExtension = rootProject.getExtensions().findByType(SonarExtension.class);
            if (sonarQubeExtension != null) {
                sonarQubeExtension.properties(p -> {
                    String githubSlug = rootProject.getProviders().gradleProperty("githubSlug").getOrNull();
                    if (githubSlug != null) {
                        p.property("sonar.projectKey", rootProject.getProviders().systemProperty("sonar.projectKey").getOrElse(githubSlug.replaceAll("/", "_")));
                        p.property("sonar.organization", rootProject.getProviders().systemProperty("sonar.organization").getOrElse("micronaut-projects"));
                        p.property("sonar.host.url", rootProject.getProviders().systemProperty("sonar.host.url").getOrElse("https://sonarcloud.io"));
                        MicronautBuildExtension micronautBuildExtension = rootProject.getExtensions().getByType(MicronautBuildExtension.class);
                        p.property("sonar.java.source", micronautBuildExtension.getJavaVersion().map(String::valueOf).get());
                        p.property("sonar.verbose", "true");
                        // Jacoco integration
                        final String jacocoReportPath = rootProject.getBuildDir().toPath()
                            .resolve("reports/jacoco/" + COVERAGE_REPORT_TASK_NAME + "/" + COVERAGE_REPORT_TASK_NAME + ".xml")
                            .toFile().getAbsolutePath();
                        p.property("sonar.coverage.jacoco.xmlReportPaths", jacocoReportPath);
                    }
                });
                // Registered once every build script has run, so that it comes after the projects' own sonar
                // blocks and adds to the criteria they declare instead of being replaced by them. A subproject
                // that declares criteria of its own replaces the root's for its module, so it gets the criterion too.
                rootProject.getGradle().projectsEvaluated(unused -> rootProject.getAllprojects().forEach(project -> {
                    SonarExtension sonar = project.getExtensions().findByType(SonarExtension.class);
                    if (sonar != null) {
                        boolean root = project == rootProject;
                        sonar.properties(p -> ignoreIssuesInTestSources(p, root));
                    }
                }));
                rootProject.getTasks().withType(SonarTask.class).configureEach(t -> t.dependsOn(COVERAGE_REPORT_TASK_NAME));
            } else {
                rootProject.getLogger().warn("Could not find the sonarqube extension");
            }
        }
    }

    /**
     * Third-party analyzers such as groovydre apply their rules to every file, test files included,
     * even though the Sonar plugin already puts the test source directories into {@code sonar.tests}.
     * The test files stay in {@code sonar.tests}, which coverage needs, but no issue is raised on them.
     *
     * @param p the Sonar properties of a project
     * @param always whether to add the criterion when the project declares no criteria, which only the root needs
     */
    private static void ignoreIssuesInTestSources(SonarProperties p, boolean always) {
        Object declared = p.getProperties().get(MULTICRITERIA);
        if (declared == null && !always) {
            return;
        }
        Set<String> criteria = new LinkedHashSet<>();
        if (declared instanceof Iterable<?> iterable) {
            iterable.forEach(criterion -> addCriteria(criteria, criterion));
        } else {
            addCriteria(criteria, declared);
        }
        criteria.add(TEST_ISSUES_CRITERION);
        p.property(MULTICRITERIA, String.join(",", criteria));
        p.property(MULTICRITERIA + "." + TEST_ISSUES_CRITERION + ".ruleKey", "*");
        p.property(MULTICRITERIA + "." + TEST_ISSUES_CRITERION + ".resourceKey", "**/src/test/**");
    }

    private static void addCriteria(Set<String> criteria, Object declared) {
        if (declared != null) {
            for (String criterion : declared.toString().split(",")) {
                if (!criterion.isBlank()) {
                    criteria.add(criterion.trim());
                }
            }
        }
    }

    private void configureJacoco(final Project rootProject) {
        rootProject.getPluginManager().apply(BasePlugin.class);
        rootProject.getPluginManager().apply(JacocoReportAggregationPlugin.class);

        ReportingExtension reporting = rootProject.getExtensions().getByType(ReportingExtension.class);
        reporting.getReports().create(COVERAGE_REPORT_TASK_NAME, JacocoCoverageReport.class, this::configureReport);

        final Configuration jacocoAggregation = rootProject.getConfigurations().getByName("jacocoAggregation");
        rootProject.getSubprojects().forEach(subproject -> {
            rootProject.evaluationDependsOn(subproject.getPath());
            subproject.getPlugins().withType(MicronautQualityChecksParticipantPlugin.class, plugin -> {
                jacocoAggregation.getDependencies().add(
                    rootProject.getDependencies().create(subproject)
                );
            });
        });
        // Register a task on the root project, so that the workflows don't fail
        // if jacocoReport is called on a BOM-only project
        rootProject.afterEvaluate(unused -> {
            if (!rootProject.getTasks().getNames().contains("jacocoTestReport")) {
                rootProject.getTasks().register("jacocoTestReport", t ->  {
                   t.setDescription("Generates aggregate Jacoco reports");
                   t.dependsOn(COVERAGE_REPORT_TASK_NAME);
                   t.doFirst(unused2 -> {
                       rootProject.getLogger().lifecycle("Generating aggregate Jacoco reports");
                   });
                });
            }
        });
    }

    private void configureReport(JacocoCoverageReport r) {
        if (GradleVersion.current().compareTo(GradleVersion.version("8.13")) < 0) {
            configureReportUsingReflection(r);
        } else {
            r.getTestSuiteName().set("test");
        }
    }

    private void configureReportUsingReflection(JacocoCoverageReport r) {
        try {
            //  r.getTestType().set(TestSuiteType.UNIT_TEST)
            Class<?> testSuiteType = Class.forName("org.gradle.api.attributes.TestSuiteType");
            var junit = testSuiteType.getDeclaredField("UNIT_TEST").get(null);
            var testType = (Property<Object>) JacocoCoverageReport.class.getDeclaredMethod("getTestType").invoke(r);
            testType.set(junit);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new GradleException("Unable to configure Jacoco report", e);
        }
    }

}
