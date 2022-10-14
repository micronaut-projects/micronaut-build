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
package io.micronaut.build.graalvm;

import io.micronaut.build.MicronautBuildExtension;
import io.micronaut.build.MicronautBuildExtensionPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public class NativeImageSupportPlugin implements Plugin<Project> {

    public static final String NATIVE_IMAGE_PROPERTIES_EXTENSION_NAME = "nativeImageProperties";
    public static final String GENERATE_NATIVE_IMAGE_PROPERTIES_TASK_NAME = "generateNativeImageProperties";

    @Override
    public void apply(Project project) {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(MicronautBuildExtensionPlugin.class);
        MicronautBuildExtension micronautBuildExtension = project.getExtensions().findByType(MicronautBuildExtension.class);
        NativeImagePropertiesExtension nativeImageExtension = ((ExtensionAware) micronautBuildExtension).getExtensions().create(NATIVE_IMAGE_PROPERTIES_EXTENSION_NAME, NativeImagePropertiesExtension.class);
        nativeImageExtension.getEnabled().convention(false);
        TaskProvider<NativePropertiesWriter> generateNativeImageProperties = project.getTasks().register(GENERATE_NATIVE_IMAGE_PROPERTIES_TASK_NAME, NativePropertiesWriter.class, task -> {
            task.onlyIf(t -> nativeImageExtension.getEnabled().get());
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/resources/native-image-properties"));
            task.getContents().convention(nativeImageExtension.getContents());
            task.getInitializeAtRuntime().convention(nativeImageExtension.getInitializeAtRuntime());
            task.getInitializeAtBuildtime().convention(nativeImageExtension.getInitializeAtBuildtime());
            task.getFeatures().convention(nativeImageExtension.getFeatures());
            task.getGroupId().convention(project.getProviders().provider(() -> String.valueOf(project.getGroup())));
            task.getArtifactId().convention(project.getProviders().provider(() -> "micronaut-" + project.getName()));
        });
        pluginManager.withPlugin("java", unused -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
            sourceSets.getByName("main").getResources().srcDir(generateNativeImageProperties);
        });
    }
}
