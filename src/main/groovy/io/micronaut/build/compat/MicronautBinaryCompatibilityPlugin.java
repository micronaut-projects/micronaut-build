/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.compat;

import io.micronaut.build.MicronautBuildExtension;
import io.micronaut.build.MicronautBuildExtensionPlugin;
import io.micronaut.build.MicronautPublishingPlugin;
import io.micronaut.build.pom.MicronautBomExtension;
import io.micronaut.build.utils.ExternalURLService;
import me.champeau.gradle.japicmp.JapicmpTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.catalog.internal.TomlFileGenerator;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A plugin which sets up binary compatibility reports for Micronaut
 * modules.
 */
public class MicronautBinaryCompatibilityPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(MicronautBuildExtensionPlugin.class);
        MicronautBuildExtension extension = project.getExtensions().getByType(MicronautBuildExtension.class);
        BinaryCompatibibilityExtension binaryCompatibility = ((ExtensionAware) extension).getExtensions().create("binaryCompatibility", BinaryCompatibibilityExtension.class);
        binaryCompatibility.getAcceptedRegressionsFile().convention(
                project.getRootProject().getLayout().getProjectDirectory().file("config/accepted-api-changes.json")
        );
        project.getPlugins().withType(MicronautPublishingPlugin.class, unused -> {
            TaskContainer tasks = project.getTasks();
            ProviderFactory providers = project.getProviders();
            project.getPluginManager().withPlugin("java-library", alsoUnused -> {
                TaskProvider<FindBaselineTask> baselineTask = registerFindBaselineTask(project, binaryCompatibility, tasks, providers);
                Provider<String> baseline = createBaselineProvider(binaryCompatibility, providers, baselineTask);
                Configuration oldClasspath = project.getConfigurations().detachedConfiguration();
                Configuration oldJar = project.getConfigurations().detachedConfiguration();
                oldClasspath.getDependencies().addLater(baseline.map(version -> project.getDependencies().create(project.getGroup() + ":micronaut-" + project.getName() + ":" + version)));
                oldJar.getDependencies().addLater(baseline.map(version -> project.getDependencies().create(project.getGroup() + ":micronaut-" + project.getName() + ":" + version + "@jar")));
                TaskProvider<JapicmpTask> japicmpTask = tasks.register("japiCmp", JapicmpTask.class, task -> {
                    task.onlyIf(t -> binaryCompatibility.getEnabled().getOrElse(true));
                    task.dependsOn(baselineTask);
                    task.getNewClasspath().from(project.getConfigurations().getByName("runtimeClasspath"));
                    task.getOldClasspath().from(oldClasspath);
                    task.getOldArchives().from(oldJar);
                    task.richReport(report -> {
                        report.getReportName().set("binary-compatibility-" + project.getName() + ".html");
                        report.getTitle().set(baseline.map(version -> "Binary compatibility report for Micronaut " + project.getName() + " " + project.getVersion() + " against " + version));
                        report.getAddDefaultRules().set(true);
                        report.addViolationTransformer(InternalMicronautTypeRule.class);
                        report.addViolationTransformer(JavaBridgeAndSyntheticRule.class);
                        report.addRule(InternalAnnotationCollectorRule.class);
                        report.addPostProcessRule(InternalAnnotationPostProcessRule.class);
                    });
                    task.getIgnoreMissingClasses().set(true);
                });
                tasks.named("check").configure(task -> task.dependsOn(japicmpTask));
                project.afterEvaluate(p -> {
                    Task jar = tasks.findByName("shadowJar");
                    if (jar == null) {
                        jar = tasks.getByName("jar");
                    }
                    Task effectiveJar = jar;
                    japicmpTask.configure(task -> {
                        File changesFile = binaryCompatibility.getAcceptedRegressionsFile().get().getAsFile();
                        if (changesFile.exists()) {
                            task.getInputs().file(changesFile).withPropertyName("accepted-api-changes").withPathSensitivity(PathSensitivity.NONE).optional(true);
                        }
                        task.getNewArchives().from(effectiveJar);
                        task.richReport(report ->
                                report.addViolationTransformer(AcceptedApiChangesRule.class,
                                        Collections.singletonMap(AcceptedApiChangesRule.CHANGES_FILE, changesFile.getAbsolutePath())
                                )
                        );
                    });
                });
            });
            project.getPluginManager().withPlugin("io.micronaut.build.internal.bom", alsoUnused -> {
                TaskProvider<FindBaselineTask> baselineTask = registerFindBaselineTask(project, binaryCompatibility, tasks, providers);
                Provider<String> baseline = createBaselineProvider(binaryCompatibility, providers, baselineTask);
                Configuration baselineConfig = project.getConfigurations().detachedConfiguration();
                baselineConfig.getDependencies().addLater(baseline.map(version -> project.getDependencies().create(project.getGroup() + ":micronaut-" + project.getName() + ":" + version + "@toml")));
                TaskProvider<VersionCatalogCompatibilityCheck> compatibilityCheckTaskProvider = tasks.register("checkVersionCatalogCompatibility", VersionCatalogCompatibilityCheck.class, task -> {
                    task.onlyIf(t -> binaryCompatibility.getEnabled().getOrElse(true));
                    task.getBaseline().fileProvider(baselineTask.map(b -> baselineConfig.getSingleFile()));
                    task.getCurrent().convention(tasks.named("generateCatalogAsToml", TomlFileGenerator.class).flatMap(TomlFileGenerator::getOutputFile));
                    task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/version-catalog-compatibility.txt"));
                    MicronautBomExtension bomExtension = project.getExtensions().findByType(MicronautBomExtension.class);
                    task.getAcceptedVersionRegressions().convention(bomExtension.getSuppressions().getAcceptedVersionRegressions());
                    task.getAcceptedLibraryRegressions().convention(bomExtension.getSuppressions().getAcceptedLibraryRegressions());
                });
                tasks.named("check").configure(task -> task.dependsOn(compatibilityCheckTaskProvider));

            });
        });
    }

    @NotNull
    private Provider<String> createBaselineProvider(BinaryCompatibibilityExtension binaryCompatibility, ProviderFactory providers, TaskProvider<FindBaselineTask> baselineTask) {
        return binaryCompatibility.getBaselineVersion().orElse(
                providers.fileContents(baselineTask.flatMap(FindBaselineTask::getPreviousVersion))
                        .getAsText().map(MicronautBinaryCompatibilityPlugin::readBaseline)
        );
    }

    @NotNull
    private TaskProvider<FindBaselineTask> registerFindBaselineTask(Project project, BinaryCompatibibilityExtension binaryCompatibility, TaskContainer tasks, ProviderFactory providers) {
        Provider<ExternalURLService> downloader = ExternalURLService.registerOn(project);
        return tasks.register("findBaseline", FindBaselineTask.class, task -> {
            task.onlyIf(t -> binaryCompatibility.getEnabled().getOrElse(true));
            task.usesService(downloader);
            task.getDownloader().set(downloader);
            task.getBaseRepository().convention("https://repo.maven.org/maven2");
            task.getGroupId().convention(providers.provider(() -> project.getGroup().toString()));
            task.getArtifactId().convention(providers.provider(() -> artifactIdOf(project)));
            task.getCurrentVersion().convention(providers.provider(() -> project.getVersion().toString()));
            task.getPreviousVersion().convention(project.getLayout().getBuildDirectory().file("baseline.txt"));
        });
    }

    private static String readBaseline(String baselineText) {
        List<String> lines = Arrays.asList(baselineText.split("[\\r\\n]+"));
        if (!lines.isEmpty()) {
            String baseline = lines.get(0);
            return baseline;
        }
        return "+";
    }

    private static String artifactIdOf(Project project) {
        String name = project.getName();
        if (name.startsWith("micronaut-")) {
            return name;
        }
        return "micronaut-" + name;
    }
}
