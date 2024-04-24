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
import org.gradle.api.initialization.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicronautGradleEnterprisePlugin implements Plugin<Settings> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicronautGradleEnterprisePlugin.class);

    @Override
    public void apply(Settings settings) {
        LOGGER.warn("The io.micronaut.build.gradle-enterprise plugin is deprecated and will be removed in a future version. Please use the io.micronaut.build.develocity plugin instead.");
        settings.getPluginManager().apply(MicronautDevelocityPlugin.class);
    }

}
