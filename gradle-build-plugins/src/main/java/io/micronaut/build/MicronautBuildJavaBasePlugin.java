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

import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

public class MicronautBuildJavaBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var pluginManager = project.getPluginManager();
        // Using a String here because the class is still implemented in Groovy so
        // not visible yet
        pluginManager.apply("io.micronaut.build.internal.base");
        pluginManager.apply(JavaPlugin.class);
        var micronautBuild = project.getExtensions().findByType(MicronautBuildExtension.class);
        configureJavaPlugin(project, micronautBuild);
    }

    private void configureJavaPlugin(Project project, MicronautBuildExtension micronautBuildExtension) {
        var javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        var toolchains = project.getExtensions().getByType(JavaToolchainService.class);

        project.afterEvaluate(p -> {
            if (micronautBuildExtension.getUseToolchains().getOrElse(false)) {
                javaPluginExtension.getToolchain().getLanguageVersion().convention(micronautBuildExtension.getJavaVersion().map(JavaLanguageVersion::of));
            } else {
                var javaVersion = micronautBuildExtension.getJavaVersion().map(JavaLanguageVersion::of).get();
                javaPluginExtension.setSourceCompatibility(javaVersion);
                javaPluginExtension.setTargetCompatibility(javaVersion);
            }
            if (micronautBuildExtension.getSourceCompatibility().isPresent() || micronautBuildExtension.getTargetCompatibility().isPresent()) {
                project.getLogger().warn("""
                    The "sourceCompatibility" and "targetCompatibility" properties are deprecated.
                    Please use "micronautBuild.javaVersion" instead.
                    You can do this directly in the project, or, better, in a convention plugin if it exists.
                    """);
                // Remove convention or Gradle will complain that you can't use both
                javaPluginExtension.getToolchain().getLanguageVersion().convention((JavaLanguageVersion) null);
                javaPluginExtension.setSourceCompatibility(micronautBuildExtension.getSourceCompatibility().orElse(micronautBuildExtension.getTargetCompatibility()).get());
                javaPluginExtension.setTargetCompatibility(micronautBuildExtension.getTargetCompatibility().orElse(micronautBuildExtension.getSourceCompatibility()).get());
            }
        });

        var useVendorAsInput = project.getProviders().environmentVariable("MICRONAUT_TEST_USE_VENDOR")
            .map(Boolean::parseBoolean).getOrElse(false);
        project.getTasks().withType(Test.class).configureEach(task -> {
            task.jvmArgs("-Duser.country=US");
            task.jvmArgs("-Duser.language=en");
            task.useJUnitPlatform();
            if (useVendorAsInput) {
                // This will have to be changed once we switch to toolchain support, since it will not be relevant anymore
                var vendor = project.getProviders().systemProperty("java.vendor").getOrElse("unknown");
                System.out.println("Configuring test task " + task.getPath() + " to execute specifically for vendor: " + vendor);
                task.getInputs().property("java.vendor", vendor);
            }
            if (task.getExtensions().findByName("develocity") != null) {
                var develocity = task.getExtensions().getByType(DevelocityTestConfiguration.class);
                develocity.testRetry(tr -> {
                    if (micronautBuildExtension.getEnvironment().isGithubAction().getOrElse(false)) {
                        tr.getMaxRetries().set(2);
                        tr.getMaxFailures().set(20);
                    }
                    tr.getFailOnPassedAfterRetry().set(false);
                });
                develocity.predictiveTestSelection(pts -> pts.getEnabled().set(micronautBuildExtension.getEnvironment().isTestSelectionEnabled()));
                develocity.getTestDistribution().getEnabled().set(false);
            }
        });
        project.afterEvaluate(unused -> {
                project.getTasks().withType(Test.class).configureEach(test -> {
                if (micronautBuildExtension.getUseToolchains().getOrElse(false)) {
                    test.getJavaLauncher().set(toolchains.launcherFor(spec -> spec.getLanguageVersion().set(micronautBuildExtension.getTestJavaVersion().map(JavaLanguageVersion::of))));
                }
            });
        });

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            var options = task.getOptions();
            options.setEncoding("UTF-8");
            options.getCompilerArgs().add("-parameters");
            micronautBuildExtension.getCompileOptions().applyTo(options);
        });
    }

}
