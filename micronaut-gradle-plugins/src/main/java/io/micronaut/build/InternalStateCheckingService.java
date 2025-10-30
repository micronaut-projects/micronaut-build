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

import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.slf4j.Logger;

public abstract class InternalStateCheckingService implements BuildService<InternalStateCheckingService.Params>, AutoCloseable {
    private static final Logger LOGGER = Logging.getLogger(InternalStateCheckingService.class);
    public static final String NAME = "internalStateCheckingService";

    interface Params extends BuildServiceParameters {
        Property<Boolean> getRegisteredByProjectPlugin();
    }

    @Override
    public void close() {
        Boolean state = getParameters().getRegisteredByProjectPlugin().get();
        if (Boolean.TRUE.equals(state)) {
            LOGGER.warn("WARNING!\n" +
                        "\n" +
                        "The internal Micronaut Build plugins have been updated, but the settings plugin hasn't been applied.\n" +
                        "You must apply the shared settings plugin by modifying the settings.gradle(.kts) file by adding on the top:\n" +
                        "\n" +
                        "pluginManagement {\n" +
                        "    repositories {\n" +
                        "        gradlePluginPortal()\n" +
                        "        mavenCentral()\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "plugins {\n" +
                        "    id(\"io.micronaut.build.shared.settings\") version \"<plugin version>\"\n" +
                        "}\n" +
                        "\n" +
                        "Not doing so will result in a failure when publishing.\n" +
                        "\n");
        }
    }
}
