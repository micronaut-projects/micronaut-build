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

    public static final String PREDICTIVE_TEST_SELECTION_ENV_VAR = "PREDICTIVE_TEST_SELECTION";
    public static final String PREDICTIVE_TEST_SELECTION_SYSPROP = "predictiveTestSelection";
    private final ProviderFactory providers;
    private final Provider<Boolean> githubAction;
    private final boolean isMigrationActive;

    public BuildEnvironment(ProviderFactory providers) {
        this.providers = providers;
        this.githubAction = trueWhenEnvVarPresent("GITHUB_ACTIONS");
        this.isMigrationActive = !providers.systemProperty("strictBuild")
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
                .map(s -> true)
                .orElse(false);
    }

    public Provider<Boolean> isTestSelectionEnabled() {
        // Predictive test selection is enabled if:
        // an environment variable is explicitly configured and set to true
        // or a system property is explicitly configured and set to true
        // or it's a local build
        Provider<String> projectGroup = providers.gradleProperty("projectGroup");
        if (projectGroup.isPresent() && projectGroup.get().startsWith("io.micronaut")) {
            return providers.environmentVariable(PREDICTIVE_TEST_SELECTION_ENV_VAR)
                    .orElse(providers.systemProperty(PREDICTIVE_TEST_SELECTION_SYSPROP))
                    .map(it -> {
                        if (it.trim().length() > 0) {
                            return Boolean.parseBoolean(it);
                        }
                        return false;
                    })
                    .orElse(isNotGithubAction());
        }
        return providers.provider(() -> false);
    }

    public void duringMigration(Runnable action) {
        if (isMigrationActive) {
            action.run();
        }
    }
}
