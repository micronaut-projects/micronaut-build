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

import io.micronaut.build.graalvm.GenerateGraalVmReachabilityMetadataTask;
import io.micronaut.build.graalvm.GraalVmReachabilityMetadataExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A plugin to be applied on the root project of a multi-project Micronaut project.
 */
public class MicronautParentPlugin implements Plugin<Project> {
    public static final String GRAALVM_REACHABILITY_METADATA_EXTENSION_NAME = "graalVmReachabilityMetadata";
    public static final String GENERATE_GRAALVM_REACHABILITY_METADATA_TASK_NAME = "generateGraalVmReachabilityMetadata";

    @Override
    public void apply(Project p) {
        var pluginManager = p.getPluginManager();
        pluginManager.apply(MicronautBuildExtensionPlugin.class);
        pluginManager.apply("io.micronaut.build.internal.docs");
        pluginManager.apply("io.micronaut.build.internal.quality-reporting");
        pluginManager.apply("io.micronaut.build.internal.parent-publishing");
        configureGraalVmReachabilityMetadata(p);
    }

    private static void configureGraalVmReachabilityMetadata(Project project) {
        var extension = project.getExtensions().create(
            GRAALVM_REACHABILITY_METADATA_EXTENSION_NAME,
            GraalVmReachabilityMetadataExtension.class
        );
        extension.getMinimumVersion().convention(project.getProviders()
            .gradleProperty("graalVmReachabilityMetadataVersion")
            .orElse(project.provider(() -> normalizeVersion(String.valueOf(project.getVersion())))));
        extension.getTestWorkflowName().convention("graalvm.yml");
        extension.getTestLevel().convention("community-tested");
        extension.getMetadataLocations().convention(List.of());
        extension.getTestsLocations().convention(project.getProviders()
            .gradleProperty("githubSlug")
            .flatMap(slug -> extension.getTestWorkflowName()
                .map(testWorkflowName -> List.of("https://github.com/" + slug + "/actions/workflows/" + testWorkflowName)))
            .orElse(List.of()));
        extension.getExcludedProjectNames().convention(List.of());

        project.getTasks().register(GENERATE_GRAALVM_REACHABILITY_METADATA_TASK_NAME, GenerateGraalVmReachabilityMetadataTask.class, task -> {
            task.getModuleDescriptions().set(project.provider(() -> collectReachabilityMetadataModules(project, extension)));
            task.getMinimumVersion().convention(extension.getMinimumVersion());
            task.getMetadataLocations().convention(extension.getMetadataLocations());
            task.getTestsLocations().convention(extension.getTestsLocations());
            task.getTestLevel().convention(extension.getTestLevel());
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("graalvm-reachability-metadata/library-and-framework-list.json"));
        });
    }

    private static Map<String, String> collectReachabilityMetadataModules(Project root, GraalVmReachabilityMetadataExtension extension) {
        var modules = new LinkedHashMap<String, String>();
        var excludedProjectNames = extension.getExcludedProjectNames().get();
        for (Project project : root.getAllprojects()) {
            if (project == root) {
                continue;
            }
            if (excludedProjectNames.contains(project.getName()) || isExcludedProject(project)) {
                continue;
            }
            if (project.getPlugins().hasPlugin(MicronautPublishingPlugin.class) || project.getPlugins().hasPlugin("maven-publish")) {
                var artifactId = MicronautPlugin.moduleNameOf(project.getName());
                var description = project.getDescription();
                modules.put(project.getGroup() + ":" + artifactId, description == null || description.isBlank() ? artifactId : description);
            }
        }
        return modules;
    }

    private static boolean isExcludedProject(Project project) {
        return project.getName().contains("bom")
            || project.getName().startsWith(MicronautPlugin.TEST_SUITE_PROJECT_PREFIX)
            || !project.getSubprojects().isEmpty();
    }

    private static String normalizeVersion(String version) {
        if (version.startsWith("v") && version.length() > 1 && Character.isDigit(version.charAt(1))) {
            return version.substring(1);
        }
        return version;
    }
}
