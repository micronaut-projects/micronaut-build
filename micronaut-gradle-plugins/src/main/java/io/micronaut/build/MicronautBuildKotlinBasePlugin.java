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

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * Configures the Kotlin compiler to use the same Java version as the Java compiler.
 * This is done using reflection because of Gradle classloading problems: we cannot add
 * the Kotlin compiler as "implementation" dependency because it would make it available
 * to all projects, preventing the use of another Kotlin plugin version.
 *
 * We can't make it compileOnly either, because the build plugins are loaded via a
 * settings plugin which is found higher in the classloader hierarchy than the project
 * plugins. Therefore, even if this code would compile, it would fail at runtime
 * with NoClassDefFoundError.
 */
public class MicronautBuildKotlinBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBuildJavaBasePlugin.class);
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", unused -> {
            try {
                var micronautBuild = project.getExtensions().getByType(MicronautBuildExtension.class);
                var kotlinExtension = project.getExtensions().findByName("kotlin");
                if (kotlinExtension != null) {
                    var getCompilerOptions = kotlinExtension.getClass().getMethod("getCompilerOptions");
                    var compilerOptions = getCompilerOptions.invoke(kotlinExtension);
                    var getJvmTarget = compilerOptions.getClass().getMethod("getJvmTarget");
                    var jvmTarget = getJvmTarget.invoke(compilerOptions);
                    var jvmTargetClass = kotlinExtension.getClass().getClassLoader().loadClass("org.jetbrains.kotlin.gradle.dsl.JvmTarget");
                    var fromTarget = jvmTargetClass.getMethod("fromTarget", String.class);
                    var javaVersion = micronautBuild.getJavaVersion().map(version -> {
                        try {
                            var versionAsString = version.toString();
                            project.getLogger().info(String.format("Configuring Kotlin to target Java %s", versionAsString));
                            return fromTarget.invoke(null, versionAsString);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    var convention = jvmTarget.getClass().getMethod("convention", Provider.class);
                    convention.invoke(jvmTarget, javaVersion);
                } else {
                    throw new RuntimeException("Kotlin project extension not found");
                }
            } catch (Exception e) {
                throw new GradleException("Unable to configure Kotlin extension", e);
            }
        });
    }
}
