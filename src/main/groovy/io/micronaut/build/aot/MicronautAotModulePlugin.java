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
package io.micronaut.build.aot;

import io.micronaut.build.MicronautBaseModulePlugin;
import io.micronaut.build.MicronautBuildExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;

/**
 * This plugin is used to implement a Micronaut AOT extension,
 * e.g. an AOT code generator.
 */
public class MicronautAotModulePlugin implements Plugin<Project> {
    private static final String DEFAULT_AOT_VERSION = "1.0.0";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBaseModulePlugin.class);
        MicronautBuildExtension micronautBuildExtension = project.getExtensions().getByType(MicronautBuildExtension.class);
        // An AOT module MUST NOT use Micronaut DI
        micronautBuildExtension.setEnableBom(false);
        micronautBuildExtension.setEnableProcessing(false);
        MicronautBuildAotExtension aotExtension = ((ExtensionAware) micronautBuildExtension).getExtensions().create("aot", MicronautBuildAotExtension.class);
        aotExtension.getVersion().convention(DEFAULT_AOT_VERSION);
        String micronautVersion = String.valueOf(project.getProperties().get("micronautVersion"));
        DependencyHandler deps = project.getDependencies();
        deps.addProvider(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME, aotExtension.getVersion().map(version -> aotModule("core", version)));
        deps.add(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME, coreModule("context", micronautVersion));
        deps.add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, coreModule("context", micronautVersion));
        deps.addProvider(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, aotExtension.getVersion().map(version -> {
            String aot = aotModule("core", version);
            ExternalModuleDependency dependency = (ExternalModuleDependency) deps.create(aot);
            dependency.capabilities(capabilities -> capabilities.requireCapability("io.micronaut.aot:aot-core-test-fixtures"));
            return dependency;
        }));
    }

    private static String aotModule(String name, String version) {
        return "io.micronaut.aot:micronaut-aot-" + name + ":" + version;
    }

    private static String coreModule(String name, String version) {
        return "io.micronaut:micronaut-" + name + ":" + version;
    }
}
