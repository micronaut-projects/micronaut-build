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

import me.champeau.gradle.igp.IncludeGitPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

/**
 * This plugin declares the Micronaut Build Settings extension.
 */
public class MicronautBuildSettingsPlugin implements Plugin<Settings> {

    @Override
    public void apply(Settings settings) {
        settings.getPluginManager().apply(IncludeGitPlugin.class);
        settings.getExtensions().create("micronautBuild", MicronautBuildSettingsExtension.class, settings);
    }

}
