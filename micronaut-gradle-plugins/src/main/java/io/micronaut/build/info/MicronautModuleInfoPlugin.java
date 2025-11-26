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
package io.micronaut.build.info;

import io.micronaut.build.MicronautBuildExtension;
import io.micronaut.build.utils.VersionParser;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault;

/**
 * A plugin which generates a Micronaut module descriptor for the project.
 */
public class MicronautModuleInfoPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var pluginManager = project.getPluginManager();
        // Using a String here because the class is still implemented in Groovy so not visible yet
        pluginManager.apply("io.micronaut.build.internal.base");
        pluginManager.apply(JavaPlugin.class);
        var micronautBuild = project.getExtensions().findByType(MicronautBuildExtension.class);
        var infoExtension = ((ExtensionAware) micronautBuild).getExtensions().create("descriptor", MicronautModuleInfoExtension.class);
        // Module descriptor generation is only enabled if compiling against Micronaut 5+
        infoExtension.getEnabled().convention(includedCoreVersion(project).orElse(versionProviderOrDefault(project, "micronaut", "")).map(v -> {
            var version = VersionParser.parse(v.replaceAll("-(SNAPSHOT|M[0-9]+)", ""));
            var minimalVersion = VersionParser.parse("5.0.0");
            return version.compareTo(minimalVersion) >= 0;
        }));
        configureDescriptorGeneration(infoExtension, project);
    }

    private static Provider<String> includedCoreVersion(Project p) {
        return p.provider(() -> {
            var includedBuilds = p.getGradle().getIncludedBuilds();
            for (var includedBuild : includedBuilds) {
                if (includedBuild.getName().equals("micronaut-core")) {
                    var path = includedBuild.getProjectDir().toPath();
                    var gradleProperties = new Properties();
                    try (var reader = Files.newBufferedReader(path.resolve("gradle.properties"))) {
                        gradleProperties.load(reader);
                    }
                    return gradleProperties.getProperty("projectVersion");

                }
            }
            return null;
        });
    }

    private void configureDescriptorGeneration(MicronautModuleInfoExtension info, Project project) {
        project.afterEvaluate(unused -> {
            if (info.getEnabled().getOrElse(true)) {
                project.getDependencies().add("compileOnly", "io.micronaut:micronaut-module-info");
                info.getModuleDescription().convention(project.provider(project::getDescription).orElse(project.getProviders().gradleProperty("projectDesc")));
                info.getModuleName().convention(project.provider(project::getName));
                info.getGroupId().convention(project.provider(() -> String.valueOf(project.getGroup())));
                info.getArtifactId().convention(project.provider(() -> determineArtifactId(project)));
                info.getModuleVersion().convention(project.provider(() -> String.valueOf(project.getVersion())));
                info.getPackageName().convention(project.provider(() -> {
                    var baseName = String.valueOf(project.getGroup());
                    return baseName.replace('-', '.') + ".info";
                }));
                info.getClassName().convention(info.getArtifactId().map(name -> {
                    var parts = name.split("[^a-zA-Z0-9]");
                    return Arrays.stream(parts)
                               .map(StringUtils::capitalize)
                               .collect(Collectors.joining()) + "ModuleInfo";
                }));
                var parentName = project.getRootProject().getName();
                var parentBaseName = parentName.substring(0, parentName.lastIndexOf("-parent"));
                info.getParentModuleId().convention(project.provider(() -> {
                    var candidates = new ArrayList<Project>();
                    for (var p : project.getRootProject().getAllprojects()) {
                        if (p.getName().equals(parentBaseName + "-core") || p.getName().equals("micronaut-" + parentBaseName + "-core")) {
                            candidates.add(p);
                        }
                    }
                    if (candidates.isEmpty()) {
                        for (var p : project.getRootProject().getAllprojects()) {
                            if (p.getName().equals("micronaut-" + parentBaseName) || p.getName().equals(parentBaseName)) {
                                candidates.add(p);
                            }
                        }
                    }
                    if (candidates.size() == 1) {
                        var p = candidates.getFirst();
                        if (p == project) {
                            // This is the main module, so it is the parent
                            return null;
                        }
                        return p.getGroup() + ":" + determineArtifactId(p);
                    }

                    throw new IllegalArgumentException("""
                Unable to determine the main subproject. You have to set the main subproject explicitly on the micronautBuild extension. For example:
                
                micronautBuild {
                   descriptor {
                       parentModuleId = "io.micronaut.foo:micronaut-foo-core"
                   }
                }
                
                The main subproject corresponds, in the multiproject build, to the project which is the "main" one, typically the core module, but NOT the BOM.
                """);
                }));
                var generator = project.getTasks().register("generateMicronautDescriptor", MicronautModuleInfoGeneratorTask.class, task -> {
                    task.getModuleDescription().convention(info.getModuleDescription());
                    task.getModuleName().convention(info.getModuleName());
                    task.getGroupId().convention(info.getGroupId());
                    task.getArtifactId().convention(info.getArtifactId());
                    task.getModuleVersion().convention(info.getModuleVersion());
                    task.getPackageName().convention(info.getPackageName());
                    task.getClassName().convention(info.getClassName());
                    task.getParentModuleId().convention(info.getParentModuleId());
                    task.getTags().convention(info.getTags());
                    task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/sources/mn-descriptor"));
                });
                var javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
                var mainSourceSet = javaPluginExtension.getSourceSets().getByName("main");
                mainSourceSet.getJava().srcDir(generator);
            }
        });
    }

    private static String determineArtifactId(Project project) {
        if (project.getName().startsWith("micronaut-")) {
            return project.getName();
        }
        return "micronaut-" + project.getName();
    }
}
