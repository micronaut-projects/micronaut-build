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
import io.micronaut.build.docs.AggregatedJavadocParticipantPlugin
import io.micronaut.build.docs.ConfigurationPropertiesPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal

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
        project.pluginManager.apply(MicronautBuildExtensionPlugin)
        project.pluginManager.apply(ConfigurationPropertiesPlugin)
        project.pluginManager.apply(AggregatedJavadocParticipantPlugin)
    }

    private void configureProjectVersion(Project project) {
        project.version = project.providers.gradleProperty("projectVersion").forUseAtConfigurationTime().orElse("undefined").get()
    }

    private void addMavenCentral(Project project) {
        // TODO: Avoid use of internal API
        Settings settings = ((GradleInternal) project.gradle).settings
        def repositoriesMode = settings.dependencyResolutionManagement.repositoriesMode.get()
        if (repositoriesMode != repositoriesMode.FAIL_ON_PROJECT_REPOS) {
            project.repositories.mavenCentral()
        }
    }

}
