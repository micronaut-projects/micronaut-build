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
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.sonatype.gradle.plugins.scan.ossindex.OssIndexPluginExtension;

import javax.inject.Inject;

public abstract class SonatypeConfigurationPlugin implements Plugin<Project> {

    @Inject
    protected abstract ProviderFactory getProviders();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.sonatype.gradle.plugins.scan");
        var ossIndexUsername = envVarOrSystemProperty("OSS_INDEX_USERNAME", "ossIndexUsername");
        var ossIndexPassword = envVarOrSystemProperty("OSS_INDEX_PASSWORD", "ossIndexPassword");
        if (ossIndexUsername.isPresent() && ossIndexPassword.isPresent()) {
            var ossIndex = project.getExtensions().getByType(OssIndexPluginExtension.class);
            ossIndex.setUsername(ossIndexUsername.get());
            ossIndex.setPassword(ossIndexPassword.get());
        }
    }

    private Provider<String> envVarOrSystemProperty(String envVar, String property) {
        return getProviders().environmentVariable(envVar)
            .orElse(getProviders().systemProperty(property));
    }
}
