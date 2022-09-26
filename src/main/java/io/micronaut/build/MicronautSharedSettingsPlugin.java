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

import io.github.gradlenexus.publishplugin.NexusPublishExtension;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static io.micronaut.build.ProviderUtils.envOrSystemProperty;

@SuppressWarnings("unused")
public class MicronautSharedSettingsPlugin implements Plugin<Settings> {
    public static final String NEXUS_STAGING_PROFILE_ID = "11bd7bc41716aa";

    @Override
    public void apply(Settings settings) {
        PluginManager pluginManager = settings.getPluginManager();
        settings.getGradle().getSharedServices().registerIfAbsent(InternalStateCheckingService.NAME, InternalStateCheckingService.class, spec -> spec.parameters(p -> p.getRegisteredByProjectPlugin().set(false))).get();
        pluginManager.apply(MicronautBuildSettingsPlugin.class);
        pluginManager.apply(MicronautGradleEnterprisePlugin.class);
        applyPublishingPlugin(settings);
        assertUniqueProjectNames(settings);
        MicronautBuildSettingsExtension buildSettingsExtension = settings.getExtensions().findByType(MicronautBuildSettingsExtension.class);
        settings.getGradle().beforeProject(p -> {
            ExtraPropertiesExtension extraProperties = p.getExtensions().getExtraProperties();
            if (!extraProperties.has("micronautVersion")) {
                extraProperties.set("micronautVersion", buildSettingsExtension.getMicronautVersion());
            }
        });
    }

    private void assertUniqueProjectNames(Settings settings) {
        settings.getGradle().projectsLoaded(gradle -> {
            Map<String, String> duplicates = new HashMap<>();
            gradle.getRootProject().getAllprojects().forEach(project -> {
                String name = project.getName();
                if (duplicates.containsKey(name)) {
                    throw new InvalidUserCodeException("Warning: Project name '" + name + "' with path '" + project.getPath() + "' has the same name as project path '" + duplicates.get(name) + "'. Please use unique names for projects.");
                }
                duplicates.put(name, project.getPath());
            });
        });
    }

    private void applyPublishingPlugin(Settings settings) {
        ProviderFactory providers = settings.getProviders();
        String ossUser = envOrSystemProperty(providers, "SONATYPE_USERNAME", "sonatypeOssUsername", "");
        String ossPass = envOrSystemProperty(providers, "SONATYPE_PASSWORD", "sonatypeOssPassword", "");
        settings.getGradle().projectsLoaded(gradle -> {
            String projectVersion = providers.gradleProperty("projectVersion").getOrElse("unspecified");
            Provider<String> projectGroup = providers.gradleProperty("projectGroup");
            gradle.getRootProject().getAllprojects().forEach(project -> {
                project.setVersion(projectVersion);
                if (projectGroup.isPresent()) {
                    project.setGroup(projectGroup.get());
                }
            });
            if (!ossUser.isEmpty() && !ossPass.isEmpty()) {
                configureNexusPublishing(gradle, ossUser, ossPass);
            }
        });
    }

    private void configureNexusPublishing(Gradle gradle, String ossUser, String ossPass) {
        Project rootProject = gradle.getRootProject();
        rootProject.getPlugins().apply("io.github.gradle-nexus.publish-plugin");
        NexusPublishExtension nexusPublish = rootProject.getExtensions().getByType(NexusPublishExtension.class);
        String version = String.valueOf(rootProject.getVersion());
        if ("unspecified".equals(version)) {
            throw new RuntimeException("The root project doesn't define a version. Please set the version in the root project.");
        }
        nexusPublish.getRepositoryDescription().set("" + rootProject.getGroup() + ":" + rootProject.getName() + ":" + version);
        nexusPublish.getUseStaging().convention(!version.endsWith("-SNAPSHOT"));
        nexusPublish.repositories(repos -> repos.create("sonatype", repo -> {
            repo.getAllowInsecureProtocol().convention(rootProject.getProviders().systemProperty("allowInsecurePublishing").map(Boolean::parseBoolean).orElse(false));
            repo.getUsername().set(ossUser);
            repo.getPassword().set(ossPass);
            repo.getNexusUrl().set(uri(envOrSystemProperty(rootProject.getProviders(), "SONATYPE_REPO_URI", "sonatypeRepoUri", "https://s01.oss.sonatype.org/service/local/")));
            repo.getSnapshotRepositoryUrl().set(uri(envOrSystemProperty(rootProject.getProviders(), "SONATYPE_SNAPSHOT_REPO_URI", "sonatypeSnapshotsRepoUri", "https://s01.oss.sonatype.org/content/repositories/snapshots/")));
            repo.getStagingProfileId().set(NEXUS_STAGING_PROFILE_ID);
        }));
    }

    private static URI uri(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


}
