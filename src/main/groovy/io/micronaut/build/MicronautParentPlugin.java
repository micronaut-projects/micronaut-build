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
 * A plugin to be applied on the root project of a multi-project Micronaut project.
 */
public class MicronautParentPlugin implements Plugin<Project> {
    @Override
    public void apply(Project p) {
        var pluginManager = p.getPluginManager();
        pluginManager.apply("io.micronaut.build.internal.docs");
        pluginManager.apply("io.micronaut.build.internal.quality-reporting");
        pluginManager.apply("io.micronaut.build.internal.parent-pulishing");
    }
}
