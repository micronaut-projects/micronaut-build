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
package io.micronaut.build.pom;

import io.micronaut.build.MicronautPlugin;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.function.Consumer;

import static io.micronaut.build.MicronautPlugin.PRE_RELEASE_CHECK_TASK_NAME;

public abstract class PomCheckerUtils {
    private PomCheckerUtils() {

    }

    public static TaskProvider<PomChecker> registerPomChecker(String taskName,
                                                              Project project,
                                                              PublishingExtension publishing,
                                                              Consumer<? super PomChecker> configuration) {
        TaskContainer tasks = project.getTasks();
        TaskProvider<PomChecker> pomChecker = tasks.register(taskName, PomChecker.class, task -> {
            String repoUrl = "https://repo.maven.apache.org/maven2/";
            ArtifactRepository repo = publishing.getRepositories().findByName("Build");
            if (repo instanceof MavenArtifactRepository) {
                repoUrl = ((MavenArtifactRepository) repo).getUrl().toString();
            }
            task.getRepositories().add(repoUrl);
            project.getRepositories().forEach(r -> {
                if (r instanceof MavenArtifactRepository) {
                    task.getRepositories().add(((MavenArtifactRepository) r).getUrl().toString());
                }
            });
            task.getPomFile().fileProvider(tasks.named("generatePomFileForMavenPublication", GenerateMavenPom.class).map(GenerateMavenPom::getDestination));
            String version = assertVersion(project);
            task.getPomCoordinates().set(project.getGroup() + ":" + MicronautPlugin.moduleNameOf(project.getName()) + ":" + version);
            task.getReportDirectory().set(project.getLayout().getBuildDirectory().dir("reports/" + taskName));
            task.getPomsDirectory().set(project.getLayout().getBuildDirectory().dir("poms"));
            ProviderFactory providers = project.getProviders();
            Provider<Boolean> failOnSnapshots =
                    providers.systemProperty("micronaut.fail.on.snapshots")
                    .orElse(providers.environmentVariable("MICRONAUT_FAIL_ON_SNAPSHOTS"))
                    .map(Boolean::parseBoolean)
                    .orElse(providers.provider(() ->
                            isPreReleaseCheck(project) || !version.endsWith("-SNAPSHOT")
                    ));
            task.getFailOnSnapshots().set(failOnSnapshots);
            task.getFailOnError().set(true);
            configuration.accept(task);
        });
        tasks.named("check").configure(task -> task.dependsOn(pomChecker));
        tasks.named(PRE_RELEASE_CHECK_TASK_NAME).configure(task -> task.dependsOn(pomChecker));
        return pomChecker;
    }

    private static boolean isPreReleaseCheck(Project project) {
        return project.getGradle()
                .getTaskGraph()
                .getAllTasks()
                .stream()
                .anyMatch(t -> t.getName().equals(PRE_RELEASE_CHECK_TASK_NAME));
    }

    public static String assertVersion(Project p) {
        String version = String.valueOf(p.getVersion());
        if (version.isEmpty() || "unspecified".equals(version)) {
            throw new GradleException("Version of " + p.getPath() + " is undefined!");
        }
        return version;
    }
}
