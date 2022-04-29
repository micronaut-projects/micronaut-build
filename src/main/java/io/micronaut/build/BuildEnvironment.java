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
package io.micronaut.build;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

public class BuildEnvironment {

    private final ProviderFactory providers;
    private final Provider<Boolean> githubAction;
    private final boolean isMigrationActive;

    public BuildEnvironment(ProviderFactory providers) {
        this.providers = providers;
        this.githubAction = trueWhenEnvVarPresent("GITHUB_ACTIONS");
        this.isMigrationActive = !providers.systemProperty("strictBuild")
                .forUseAtConfigurationTime()
                .isPresent();
    }

    public Provider<Boolean> isGithubAction() {
        return githubAction;
    }

    public Provider<Boolean> isNotGithubAction() {
        return githubAction.map(b -> !b);
    }

    public Provider<Boolean> trueWhenEnvVarPresent(String envVar) {
        return providers.environmentVariable(envVar)
                .forUseAtConfigurationTime()
                .map(s -> true)
                .orElse(false);
    }

    public Provider<Boolean> isTestSelectionEnabled() {
        // Predictive test selection is enabled if:
        // an environment variable is explicitly configured and set to true
        // or a system property is explicitly configured and set to true
        // or it's a local build
        return providers.environmentVariable("PREDICTIVE_TEST_SELECTION")
                .orElse(providers.systemProperty("predictiveTestSelection"))
                .map(it -> {
                    if (it.trim().length() > 0) {
                        return Boolean.parseBoolean(it);
                    }
                    return false;
                })
                .orElse(isNotGithubAction());
    }

    public void duringMigration(Runnable action) {
        if (isMigrationActive) {
            action.run();
        }
    }
}
