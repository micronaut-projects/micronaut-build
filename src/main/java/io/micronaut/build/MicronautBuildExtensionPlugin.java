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

/**
 * This plugin is responsible for creating the Micronaut Build
 * extension.
 */
public abstract class MicronautBuildExtensionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        createBuildExtension(project);
    }

    private void createBuildExtension(Project project) {
        BuildEnvironment buildEnvironment = new BuildEnvironment(project.getProviders());
        var micronautBuild = project.getExtensions().create("micronautBuild", MicronautBuildExtension.class, buildEnvironment);
        micronautBuild.getTestFramework().convention(TestFramework.SPOCK);
    }
}
