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

public class ProviderUtils {
    public static boolean guessCI(ProviderFactory providers) {
        return providers
                .environmentVariable("CI")
                .flatMap(s -> // Not all workflows may have the enterprise key set
                        providers.environmentVariable("GRADLE_ENTERPRISE_ACCESS_KEY")
                                .map(env -> true)
                                .orElse(false)
                )
                .orElse(false)
                .get();
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
