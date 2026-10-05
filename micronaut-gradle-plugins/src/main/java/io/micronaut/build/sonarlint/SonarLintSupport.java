/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.sonarlint;

import io.micronaut.build.utils.DefaultVersions;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.List;

import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault;

/**
 * Registers the {@code sonarLint} task on Java projects.
 */
public final class SonarLintSupport {

    public static final String TASK_NAME = "sonarLint";
    public static final String EXPORT_RULES_TASK_NAME = "sonarLintExportRules";
    public static final String BASE_REF_PROPERTY = "sonarLint.baseRef";
    public static final String ALL_PROPERTY = "sonarLint.all";
    public static final String DEFAULT_BASE_REF = "origin/HEAD";

    private SonarLintSupport() {
    }

    public static void configure(Project project, SonarLintExtension extension) {
        project.getPluginManager().withPlugin("java", unused -> doConfigure(project, extension));
    }

    private static void doConfigure(Project project, SonarLintExtension extension) {
        ProviderFactory providers = project.getProviders();
        var configurations = project.getConfigurations();
        var dependencies = project.getDependencies();

        DependencyScopeConfiguration engine = configurations.dependencyScope("sonarLintEngine").get();
        engine.setDescription("The SonarLint analysis engine");
        dependencies.addProvider(engine.getName(), versionProviderOrDefault(project, "sonarlint_engine", DefaultVersions.SONARLINT_ENGINE_VERSION)
            .map(v -> "org.sonarsource.sonarlint.core:sonarlint-analysis-engine:" + v));
        dependencies.addProvider(engine.getName(), versionProviderOrDefault(project, "sonarlint_slf4j", DefaultVersions.SONARLINT_SLF4J_VERSION)
            .map(v -> "org.slf4j:slf4j-nop:" + v));
        ResolvableConfiguration engineClasspath = configurations.resolvable("sonarLintEngineClasspath", c -> c.extendsFrom(engine)).get();

        DependencyScopeConfiguration analyzers = configurations.dependencyScope("sonarLintAnalyzers").get();
        analyzers.setDescription("The SonarSource analyzer plugins the sonarLint task runs");
        addAnalyzer(project, analyzers, "sonar_java", DefaultVersions.SONAR_JAVA_VERSION, "org.sonarsource.java:sonar-java-plugin:");
        addAnalyzer(project, analyzers, "sonar_java_symbolic_execution", DefaultVersions.SONAR_JAVA_SYMBOLIC_EXECUTION_VERSION,
            "org.sonarsource.java:sonar-java-symbolic-execution-plugin:");
        ResolvableConfiguration analyzersClasspath = configurations.resolvable("sonarLintAnalyzersClasspath", c -> {
            c.extendsFrom(analyzers);
            c.setTransitive(false);
        }).get();

        Provider<String> baseRef = providers.gradleProperty(BASE_REF_PROPERTY)
            .orElse(extension.getBaseRef())
            .orElse(DEFAULT_BASE_REF);
        Provider<Boolean> allFiles = providers.gradleProperty(ALL_PROPERTY)
            .map(v -> v.isEmpty() || Boolean.parseBoolean(v))
            .orElse(extension.getAllFiles());
        Provider<String> changedLines = providers.of(GitChangedLines.class, spec -> {
            spec.getParameters().getDirectory().set(project.getLayout().getProjectDirectory());
            spec.getParameters().getRef().set(baseRef);
        });

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        TaskProvider<JavaCompile> compileJava = project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class);
        TaskProvider<JavaCompile> compileTestJava = project.getTasks().named(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaCompile.class);
        Provider<Boolean> includeTests = extension.getIncludeTests();

        TaskProvider<SonarLintTask> sonarLint = project.getTasks().register(TASK_NAME, SonarLintTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Analyses the Java sources changed since the base ref with SonarSource's Java analyzer and the SonarCloud rules, offline");
            task.getMainSources().from(main.getJava().getSourceDirectories());
            task.getTestSources().from(includeTests.map(t -> t ? test.getJava().getSourceDirectories() : List.of()));
            task.getMainBinaries().from(main.getOutput().getClassesDirs());
            task.getMainLibraries().from(main.getCompileClasspath());
            task.getTestBinaries().from(includeTests.map(t -> t ? List.of(compileTestJava.flatMap(JavaCompile::getDestinationDirectory)) : List.of()));
            task.getTestLibraries().from(includeTests.map(t -> t ? test.getCompileClasspath() : List.of()));
            task.getEngineClasspath().from(engineClasspath);
            task.getAnalyzers().from(analyzersClasspath);
            task.getRulesFile().set(extension.getRulesFile());
            task.getSkippedRules().set(extension.getSkippedRules());
            task.getGateTypes().set(extension.getGateTypes());
            task.getGateSeverities().set(extension.getGateSeverities());
            task.getFailOnIssues().set(extension.getFailOnIssues());
            task.getShowAllIssues().set(extension.getShowAllIssues());
            task.getAllFiles().set(allFiles);
            task.getExcludes().set(extension.getExcludes());
            task.getJavaSource().set(compileJava.flatMap(c -> c.getOptions().getRelease().map(String::valueOf)
                .orElse(project.provider(() -> project.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility().toString()))));
            task.getChangedLines().set(allFiles.flatMap(all -> all ? providers.provider(() -> null) : changedLines));
            task.getRootDirectory().set(project.getRootProject().getLayout().getProjectDirectory());
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            task.getJsonReport().set(project.getLayout().getBuildDirectory().file("reports/sonarlint/sonarlint.json"));
            task.getSarifReport().set(project.getLayout().getBuildDirectory().file("reports/sonarlint/sonarlint.sarif"));
        });
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(check ->
            check.dependsOn(extension.getEnabled().map(enabled -> enabled ? List.<TaskProvider<? extends Task>>of(sonarLint) : List.of())));
    }

    private static void addAnalyzer(Project project, Configuration configuration, String alias, String defaultVersion, String coordinates) {
        project.getDependencies().addProvider(configuration.getName(),
            versionProviderOrDefault(project, alias, defaultVersion).map(v -> coordinates + v),
            (ExternalModuleDependency d) -> d.setTransitive(false));
    }

    /**
     * Registers the task that exports the rules of a SonarCloud quality profile, on the root project.
     *
     * @param rootProject the root project
     */
    public static void registerExportRules(Project rootProject) {
        ProviderFactory providers = rootProject.getProviders();
        rootProject.getTasks().register(EXPORT_RULES_TASK_NAME, SonarLintExportRulesTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Exports the active rules of a SonarCloud quality profile for the sonarLint task");
            task.getOrganization().convention(providers.gradleProperty("sonarLint.organization").orElse(SonarLintExportRulesTask.DEFAULT_ORGANIZATION));
            task.getQualityProfile().convention(providers.gradleProperty("sonarLint.qualityProfile").orElse(SonarLintExportRulesTask.DEFAULT_QUALITY_PROFILE));
            task.getServerUrl().convention("https://sonarcloud.io");
            task.getOutputFile().convention(rootProject.getLayout().getProjectDirectory().file("config/sonarlint/rules.json"));
        });
    }
}
