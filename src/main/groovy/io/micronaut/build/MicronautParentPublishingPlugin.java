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
package io.micronaut.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;

/**
 * This plugin should only be applied to the root project, and is responsible
 * for preparing a bundle for use with the Maven Central Publisher API.
 */
public class MicronautParentPublishingPlugin implements Plugin<Project> {
    @Override
    public void apply(Project p) {
        var rootProject = p.getRootProject();
        if (!p.equals(rootProject)) {
            throw new IllegalStateException("This plugin should only be applied to the root project");
        }
        var version = String.valueOf(p.getVersion());
        if (version.endsWith("-SNAPSHOT")) {
            // Not supported for SNAPSHOTs
            return;
        }
        var tasks = rootProject.getTasks();
        var layout = rootProject.getLayout();
        var buildRepoDirectory = layout.getBuildDirectory().dir("repo");
        var cleanRepo = tasks.register("cleanRepo", Delete.class, t -> t.delete(buildRepoDirectory));
        var prepareBundle = registerPrepareBundleTask(tasks, cleanRepo, rootProject, layout, buildRepoDirectory);
        var providers = rootProject.getProviders();
        var mavenPublish = tasks.register("publishToMavenCentral", MavenCentralPublishTask.class, task -> {
            task.getBundle().convention(prepareBundle.flatMap(AbstractArchiveTask::getArchiveFile));
            task.getUsername().convention(envVarOrSystemProp(providers, "SONATYPE_USERNAME", "sonatypeOssUsername"));
            task.getPassword().convention(envVarOrSystemProp(providers, "SONATYPE_PASSWORD", "sonatypeOssPassword"));
            task.getPublishingType().convention(MavenCentralPublishTask.PublishingType.USER_MANAGED);
        });
    }

    private static TaskProvider<Tar> registerPrepareBundleTask(TaskContainer tasks, TaskProvider<Delete> cleanRepo, Project rootProject, ProjectLayout layout, Provider<Directory> buildRepoDirectory) {
        return tasks.register("prepareBundle", Tar.class, config -> {
            config.dependsOn(cleanRepo);
            config.mustRunAfter(cleanRepo);
            config.setCompression(Compression.GZIP);
            // The way to configure the dependency is super ugly, but we don't have a reliable
            // method to trigger the publishing from all projects directly.
            rootProject.getSubprojects().forEach(subproject -> {
                if (subproject.getTasks().getNames().contains("publishAllPublicationsToBuildRepository")) {
                    config.dependsOn(subproject.getPath() + ":" + "publishAllPublicationsToBuildRepository");
                }
            });
            config.getDestinationDirectory().convention(layout.getBuildDirectory().dir("maven-bundle"));
            config.getArchiveBaseName().convention(rootProject.getName() + "-maven-bundle");
            config.getArchiveVersion().convention(rootProject.provider(() -> String.valueOf(rootProject.getVersion())));
            config.from(buildRepoDirectory);
        });
    }

    public static Provider<String> envVarOrSystemProp(ProviderFactory providers,
                                                      String envVarName,
                                                      String systemPropName) {
        return providers.environmentVariable(envVarName).orElse(providers.systemProperty(systemPropName));
    }
}
