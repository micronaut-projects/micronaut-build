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
package io.micronaut.build.utils;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.io.File;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;

public class ProviderUtils {
    private static final Pattern TRUSTED_BRANCH_REF = Pattern.compile("refs/heads/(master|main|[0-9]+\\.[0-9]+\\.x)");

    public static boolean guessCI(ProviderFactory providers) {
        return guessCI(name -> providers.environmentVariable(name).getOrNull());
    }

    static boolean guessCI(Function<String, String> environment) {
        // Not all workflows have a Develocity access key set. setup-gradle exports short-lived tokens
        // under both names; workflows that pass the key themselves use DEVELOCITY_ACCESS_KEY.
        return environment.apply("CI") != null
            && (isNotBlank(environment.apply("DEVELOCITY_ACCESS_KEY"))
            || isNotBlank(environment.apply("GRADLE_ENTERPRISE_ACCESS_KEY")));
    }

    /**
     * Whether this is a GitHub Actions build of a push to a release or default branch. Only such builds
     * write to the remote build cache by default: pull requests, merge queues, {@code workflow_run},
     * scheduled and manually dispatched builds may run code that has not been merged.
     *
     * @param providers the provider factory
     * @return true for a {@code push} event to {@code master}, {@code main} or an {@code x.y.x} branch
     */
    public static boolean isTrustedGitHubPush(ProviderFactory providers) {
        return isTrustedGitHubPush(name -> providers.environmentVariable(name).getOrNull());
    }

    static boolean isTrustedGitHubPush(Function<String, String> environment) {
        String ref = environment.apply("GITHUB_REF");
        return "push".equals(environment.apply("GITHUB_EVENT_NAME"))
            && ref != null
            && TRUSTED_BRANCH_REF.matcher(ref).matches();
    }

    /**
     * Finds the Develocity access key, ignoring blank values so that an empty variable does not hide a
     * configured one.
     *
     * @param providers the provider factory
     * @return the access key, or null if none is configured
     */
    public static String findDevelocityAccessKey(ProviderFactory providers) {
        return findDevelocityAccessKey(
            name -> providers.environmentVariable(name).getOrNull(),
            name -> providers.systemProperty(name).getOrNull()
        );
    }

    static String findDevelocityAccessKey(Function<String, String> environment, Function<String, String> systemProperties) {
        String[] candidates = {
            environment.apply("DEVELOCITY_ACCESS_KEY"),
            environment.apply("GRADLE_ENTERPRISE_ACCESS_KEY"),
            // kept for backward compatibility
            systemProperties.apply("GRADLE_ENTERPRISE_ACCESS_KEY")
        };
        for (String candidate : candidates) {
            if (isNotBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static String envOrSystemProperty(ProviderFactory providers, String envName, String propertyName, String defaultValue) {
        return providers.environmentVariable(envName)
                .orElse(providers.gradleProperty(propertyName))
                .orElse(providers.systemProperty(propertyName))
                .getOrElse(defaultValue);
    }

    /**
     * Returns a provider for a property that can be defined in gradle.properties, but can also look for
     * a Gradle property in the project hierarchy (useful in the context of an included build).
     * @param providers the provider factory
     * @param baseDir the base directory where to search for gradle.properties files
     * @param propertyName the name of the property
     * @return a provider for the property
     */
    public static Provider<String> fromGradleProperty(ProviderFactory providers, File baseDir, String propertyName) {
        return providers.gradleProperty(propertyName)
            .orElse(providers.provider(() -> {
                var dir = baseDir;
                while (dir.getParentFile() != null) {
                    var gradleProperties = new File(dir, "gradle.properties");
                    if (gradleProperties.exists()) {
                        var props  = new Properties();
                        try (var reader = new java.io.FileReader(gradleProperties)) {
                            props.load(reader);
                            var property = props.getProperty(propertyName);
                            if (property != null) {
                                return property;
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Error reading gradle.properties", e);
                        }
                    }
                    dir = dir.getParentFile();
                }
                return null;
            }));
    }
}
