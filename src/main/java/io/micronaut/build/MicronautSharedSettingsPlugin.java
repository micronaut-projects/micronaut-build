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

import com.gradle.CommonCustomUserDataGradlePlugin;
import com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension;
import com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin;
import com.gradle.scan.plugin.BuildScanExtension;
import io.github.gradlenexus.publishplugin.NexusPublishExtension;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.caching.http.HttpBuildCache;
import org.nosphere.gradle.github.ActionsPlugin;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class MicronautSharedSettingsPlugin implements Plugin<Settings> {
    public static final String NEXUS_STAGING_PROFILE_ID = "11bd7bc41716aa";

    @Override
    public void apply(Settings settings) {
        PluginManager pluginManager = settings.getPluginManager();
        pluginManager.apply(GradleEnterprisePlugin.class);
        pluginManager.apply(CommonCustomUserDataGradlePlugin.class);
        GradleEnterpriseExtension ge = settings.getExtensions().getByType(GradleEnterpriseExtension.class);
        MicronautBuildSettingsExtension micronautBuildSettingsExtension = settings.getExtensions().create("micronautBuild", MicronautBuildSettingsExtension.class);
        configureGradleEnterprise(settings, ge, micronautBuildSettingsExtension);
        applyPublishingPlugin(settings);
        assertUniqueProjectNames(settings);
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
            String projectVersion = providers.gradleProperty("projectVersion").forUseAtConfigurationTime().getOrElse("unspecified");
            Provider<String> projectGroup = providers.gradleProperty("projectGroup").forUseAtConfigurationTime();
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
            repo.getAllowInsecureProtocol().convention(rootProject.getProviders().systemProperty("allowInsecurePublishing").forUseAtConfigurationTime().map(Boolean::parseBoolean).orElse(false));
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

    private static String envOrSystemProperty(ProviderFactory providers, String envName, String propertyName, String defaultValue) {
        return providers.environmentVariable(envName)
                .forUseAtConfigurationTime()
                .orElse(providers.gradleProperty(propertyName).forUseAtConfigurationTime())
                .getOrElse(defaultValue);
    }

    private void configureGradleEnterprise(Settings settings,
                                           GradleEnterpriseExtension ge,
                                           MicronautBuildSettingsExtension micronautBuildSettingsExtension) {
        ProviderFactory providers = settings.getProviders();
        Provider<Boolean> publishScanOnDemand = providers.gradleProperty("publishScanOnDemand")
                .forUseAtConfigurationTime()
                .map(Boolean::parseBoolean)
                .orElse(false);
        boolean isCI = guessCI(providers);
        configureBuildScansPublishing(ge, isCI, publishScanOnDemand);
        settings.getGradle().projectsLoaded(MicronautSharedSettingsPlugin::applyGitHubActionsPlugin);
        if (providers.gradleProperty("org.gradle.caching").forUseAtConfigurationTime().map(Boolean::parseBoolean).orElse(true).get()) {
            settings.getGradle().settingsEvaluated(lateSettings -> {
                BuildCacheConfiguration buildCache = settings.getBuildCache();
                boolean localEnabled = micronautBuildSettingsExtension.getUseLocalCache().get();
                boolean remoteEnabled = micronautBuildSettingsExtension.getUseRemoteCache().get();
                boolean push = MicronautBuildSettingsExtension.booleanProvider(providers, "cachePush", isCI).get();
                if (isCI) {
                    System.out.println("Build cache     enabled     push");
                    System.out.println("    Local        " + (localEnabled ? "   Y   " : "   N   ") + "     N/A");
                    System.out.println("    Remote       " + (remoteEnabled ? "   Y   " : "   N   ") + "     " + (push ? " Y" : " N"));
                }
                buildCache.getLocal().setEnabled(localEnabled);
                if (remoteEnabled) {
                    buildCache.remote(HttpBuildCache.class, remote -> {
                        remote.setUrl("https://ge.micronaut.io/cache/");
                        remote.setEnabled(true);
                        if (push) {
                            String username = envOrSystemProperty(providers, "GRADLE_ENTERPRISE_CACHE_USERNAME", "gradleEnterpriseCacheUsername", "");
                            String password = envOrSystemProperty(providers, "GRADLE_ENTERPRISE_CACHE_PASSWORD", "gradleEnterpriseCachePassword", "");
                            if (!username.isEmpty() && !password.isEmpty()) {
                                remote.setPush(true);
                                remote.credentials(creds -> {
                                    creds.setUsername(username);
                                    creds.setPassword(password);
                                });
                            } else {
                                System.err.println("WARNING: No credentials for remote build cache, cannot configure push!");
                            }
                        }
                    });
                }
            });
        }
    }

    private void configureBuildScansPublishing(GradleEnterpriseExtension ge, boolean isCI, Provider<Boolean> publishScanOnDemand) {
        ge.setServer("https://ge.micronaut.io");
        ge.buildScan(buildScan -> {
            if (!publishScanOnDemand.get()) {
                buildScan.publishAlways();
            }
            if (isCI) {
                buildScan.setUploadInBackground(false);
            } else {
                publishIfAuthenticated(buildScan);
            }
            buildScan.capture(c ->
                    c.setTaskInputFiles(true)
            );
        });
    }

    private static void applyGitHubActionsPlugin(Gradle gradle) {
        gradle.getRootProject().getPluginManager().apply(ActionsPlugin.class);
    }

    private static void publishIfAuthenticated(BuildScanExtension ext) {
        try {
            ext.getClass().getMethod("publishIfAuthenticated").invoke(ext);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            System.err.println("Unable to set publish if authenticated on build scan extension");
        }
    }

    private static boolean guessCI(ProviderFactory providers) {
        return providers
                .environmentVariable("CI").forUseAtConfigurationTime()
                .flatMap(s -> // Not all workflows may have the enterprise key set
                        providers.environmentVariable("GRADLE_ENTERPRISE_ACCESS_KEY")
                                .forUseAtConfigurationTime()
                                .map(env -> true)
                                .orElse(false)
                )
                .orElse(false)
                .get();
    }
}
