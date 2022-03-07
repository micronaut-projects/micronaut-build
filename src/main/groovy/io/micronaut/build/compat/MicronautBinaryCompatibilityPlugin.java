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

import com.google.common.io.Files;
import io.micronaut.build.MicronautPublishingPlugin;
import me.champeau.gradle.japicmp.JapicmpTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * A plugin which sets up binary compatibility reports for Micronaut
 * modules.
 */
public class MicronautBinaryCompatibilityPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().withType(MicronautPublishingPlugin.class, unused -> {
            project.getPluginManager().withPlugin("java-library", alsoUnused -> {
                TaskContainer tasks = project.getTasks();
                ProviderFactory providers = project.getProviders();
                TaskProvider<FindBaselineTask> baseline = tasks.register("findBaseline", FindBaselineTask.class, task -> {
                    task.getGithubSlug().convention(providers.gradleProperty("githubSlug"));
                    task.getCurrentVersion().convention(providers.provider(() -> project.getVersion().toString()));
                    task.getPreviousVersion().convention(project.getLayout().getBuildDirectory().file("baseline.txt"));
                });
                Configuration oldClasspath = project.getConfigurations().detachedConfiguration();
                Configuration oldJar = project.getConfigurations().detachedConfiguration();
                oldClasspath.getDependencies().addLater(baseline.map(baselineTask -> {
                    String version = readBaseline(baselineTask);
                    return project.getDependencies().create(project.getGroup() + ":micronaut-" + project.getName() + ":" + version);
                }));
                oldJar.getDependencies().addLater(baseline.map(baselineTask -> {
                    String version = readBaseline(baselineTask);
                    return project.getDependencies().create(project.getGroup() + ":micronaut-" + project.getName() + ":" + version + "@jar");
                }));
                RegularFile changesFile = project.getRootProject().getLayout().getProjectDirectory().file("accepted-api-changes.json");
                TaskProvider<JapicmpTask> japicmpTask = tasks.register("japiCmp", JapicmpTask.class, task -> {
                    task.getNewClasspath().from(project.getConfigurations().getByName("runtimeClasspath"));
                    task.getOldClasspath().from(oldClasspath);
                    task.getOldArchives().from(oldJar);
                    task.getInputs().property("accepted-api-changes", providers.provider(changesFile::getAsFile)).optional(true);
                    task.richReport(report -> {
                        report.getReportName().set("binary-compatibility-" + project.getName() + ".html");
                        report.getTitle().set(baseline.map(baselineTask -> {
                            String version = readBaseline(baselineTask);
                            return "Binary compatibility report for Micronaut " + project.getName() + " " + project.getVersion() + " against " + version;
                        }));
                        report.getAddDefaultRules().set(true);
                        report.addViolationTransformer(InternalMicronautTypeRule.class);
                        report.addViolationTransformer(AcceptedApiChangesRule.class,
                                Collections.singletonMap(AcceptedApiChangesRule.CHANGES_FILE, changesFile.getAsFile().getAbsolutePath())
                        );
                    });
                    task.getIgnoreMissingClasses().set(true);
                });
                project.afterEvaluate(p -> {
                    Task jar = tasks.findByName("shadowJar");
                    if (jar == null) {
                        jar = tasks.getByName("jar");
                    }
                    Task effectiveJar = jar;
                    japicmpTask.configure(task -> task.getNewArchives().from(effectiveJar));
                });
            });
        });
    }

    private static String readBaseline(FindBaselineTask baselineTask) {
        List<String> lines;
        try {
            lines = Files.readLines(baselineTask.getPreviousVersion().getAsFile().get(), StandardCharsets.UTF_8);
            if (lines.size() > 0) {
                return lines.get(0);
            }
        } catch (IOException e) {
            return "+";
        }
        return "+";
    }
}
