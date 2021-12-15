/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package io.micronaut.build

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal

@CompileStatic
class MicronautDependencyResolutionConfigurationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        addMavenCentral(project)
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
