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
import io.micronaut.build.utils.ProviderUtils;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.micronaut.build.utils.ProviderUtils.envOrSystemProperty;

@SuppressWarnings("unused")
public class MicronautSharedSettingsPlugin implements MicronautPlugin<Settings> {
    private static final Logger LOGGER = Logging.getLogger(MicronautSharedSettingsPlugin.class);

    public static final String NEXUS_STAGING_PROFILE_ID = "11bd7bc41716aa";
    private static final String STANDARDIZED_PROJECT_NAMES_WARNING = "=========================================================\n" +
                                                                     "Standardized project names are disabled.\n" +
                                                                     "Consider enabling them with\n" +
                                                                     "    micronautBuild.useStandardizedProjectNames=true\n" +
                                                                     "in settings.gradle.\n\n" +
                                                                     "Then you will need to replace project dependencies from\n" +
                                                                     "   project(':foo')\n" +
                                                                     "to\n" +
                                                                     "   project(':micronaut-foo')\n" +
                                                                     "in your build scripts\n" +
                                                                     "=========================================================";

    @Override
    public void apply(Settings settings) {
        PluginManager pluginManager = settings.getPluginManager();
        settings.getGradle().getSharedServices().registerIfAbsent(InternalStateCheckingService.NAME, InternalStateCheckingService.class, spec -> spec.parameters(p -> p.getRegisteredByProjectPlugin().set(false))).get();
        pluginManager.apply(MicronautBuildSettingsPlugin.class);
        pluginManager.apply(MicronautDevelocityPlugin.class);
        MicronautBuildSettingsExtension buildSettingsExtension = settings.getExtensions().findByType(MicronautBuildSettingsExtension.class);
        applyPublishingPlugin(settings);
        configureProjectNames(settings, buildSettingsExtension);
        assertUniqueProjectNames(settings);
        settings.getGradle().beforeProject(p -> {
            ExtraPropertiesExtension extraProperties = p.getExtensions().getExtraProperties();
            if (!extraProperties.has("micronautVersion")) {
                extraProperties.set("micronautVersion", buildSettingsExtension.getMicronautVersion());
            }
        });
    }

    private void configureProjectNames(Settings settings, MicronautBuildSettingsExtension buildSettings) {
        settings.getGradle().settingsEvaluated(unused -> {
            Boolean useStandardProjectNames = buildSettings.getUseStandardizedProjectNames().get();
            if (Boolean.TRUE.equals(useStandardProjectNames)) {
                Set<ProjectDescriptor> visited = new HashSet<>();
                configureProjectName(settings.getRootProject(), visited, buildSettings);
            } else {
                LOGGER.warn(STANDARDIZED_PROJECT_NAMES_WARNING);
            }
        });
    }

    private void configureProjectName(ProjectDescriptor project, Set<ProjectDescriptor> visited, MicronautBuildSettingsExtension extension) {
        if (visited.add(project)) {
            String path = project.getPath();
            if (!(":".equals(path))) {
                String name = project.getName();
                List<String> ignoredNamePrefixes = extension.getNonStandardProjectNamePrefixes().getOrElse(Collections.emptyList());
                List<String> ignoredPathPrefixes = extension.getNonStandardProjectPathPrefixes().getOrElse(Collections.emptyList());
                boolean nameIsNotIgnored = ignoredNamePrefixes.stream().noneMatch(name::startsWith);
                boolean pathIsNotIgnored = ignoredPathPrefixes.stream().noneMatch(path::startsWith);
                LOGGER.debug("[Automatic project renaming] Project name: {}, path: {}, nameIsNotIgnored: {}, pathIsNotIgnored: {}", name, path, nameIsNotIgnored, pathIsNotIgnored);
                if (nameIsNotIgnored && pathIsNotIgnored) {
                    name = MICRONAUT_PROJECT_PREFIX + name;
                    project.setName(name);
                }
            }
            for (ProjectDescriptor child : project.getChildren()) {
                configureProjectName(child, visited, extension);
            }
        }
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
            String projectVersion = ProviderUtils.fromGradleProperty(providers, settings.getRootDir(), "projectVersion").getOrElse("undefined");
            Provider<String> projectGroup = ProviderUtils.fromGradleProperty(providers, settings.getRootDir(), "projectGroup");
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
            repo.getNexusUrl().set(uri(envOrSystemProperty(rootProject.getProviders(), "SONATYPE_REPO_URI", "sonatypeRepoUri", "https://ossrh-staging-api.central.sonatype.com/service/local/")));
            repo.getSnapshotRepositoryUrl().set(uri(envOrSystemProperty(rootProject.getProviders(), "SONATYPE_SNAPSHOT_REPO_URI", "sonatypeSnapshotsRepoUri", "https://central.sonatype.com/repository/maven-snapshots/")));
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
