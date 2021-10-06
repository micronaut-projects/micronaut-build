/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

/**
 * This plugin is responsible for creating the Micronaut Build
 * project extension and configuring reasonable defaults.
 */
@CompileStatic
class MicronautBasePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        addMavenCentral(project)
        configureProjectVersion(project)
        createBuildExtension(project)
    }

    private void configureProjectVersion(Project project) {
        project.version = project.providers.gradleProperty("projectVersion").forUseAtConfigurationTime().orElse("undefined").get()
    }

    private MavenArtifactRepository addMavenCentral(Project project) {
        project.repositories.mavenCentral()
    }

    private void createBuildExtension(Project project) {
        def buildEnvironment = new BuildEnvironment(project.providers)
        project.extensions.create("micronautBuild", MicronautBuildExtension, buildEnvironment)
    }

}
