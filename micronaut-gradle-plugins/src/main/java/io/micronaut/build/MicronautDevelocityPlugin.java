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

import com.gradle.CommonCustomUserDataGradlePlugin;
import com.gradle.develocity.agent.gradle.DevelocityConfiguration;
import com.gradle.develocity.agent.gradle.DevelocityPlugin;
import io.micronaut.build.utils.ProviderUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.nosphere.gradle.github.ActionsPlugin;

import static io.micronaut.build.BuildEnvironment.PREDICTIVE_TEST_SELECTION_ENV_VAR;

public class MicronautDevelocityPlugin implements Plugin<Settings> {
    private static final String[] SAFE_TO_LOG_ENV_VARIABLES = new String[]{
        PREDICTIVE_TEST_SELECTION_ENV_VAR
    };

    @Override
    public void apply(Settings settings) {
        var pluginManager = settings.getPluginManager();
        pluginManager.apply(MicronautBuildSettingsPlugin.class);
        if (!isDevelocityEnabled(settings)) {
            return;
        }
        pluginManager.apply(DevelocityPlugin.class);
        pluginManager.apply(CommonCustomUserDataGradlePlugin.class);
        Provider<String> projectGroup = settings.getProviders().gradleProperty("projectGroup");
        if (projectGroup.isPresent() && !projectGroup.get().startsWith("io.micronaut")) {
            // Do not apply the Gradle Enterprise plugin if the project doesn't belong to
            // our own group
            return;
        }
        var config = settings.getExtensions().getByType(DevelocityConfiguration.class);
        var micronautBuildSettingsExtension = settings.getExtensions().getByType(MicronautBuildSettingsExtension.class);
        configureDevelocity(settings, config, micronautBuildSettingsExtension);
    }

    private static boolean isDevelocityEnabled(Settings settings) {
        return booleanProperty(settings, "develocity.enabled", true);
    }

    private static boolean booleanProperty(Settings settings, String name, boolean defaultValue) {
        return settings.getProviders()
            .gradleProperty(name)
            .map(Boolean::parseBoolean)
            .getOrElse(defaultValue);
    }

    private void configureDevelocity(Settings settings,
                                     DevelocityConfiguration config,
                                     MicronautBuildSettingsExtension micronautBuildSettingsExtension) {
        var providers = settings.getProviders();
        var publishScanOnDemand = booleanProperty(settings, "publishScanOnDemand", false);
        var isCI = ProviderUtils.guessCI(providers);
        var buildScanExplicit = settings.getStartParameter().isBuildScan();
        configureBuildScansPublishing(config, isCI, publishScanOnDemand, buildScanExplicit);
        settings.getGradle().projectsLoaded(MicronautDevelocityPlugin::applyGitHubActionsPlugin);
        tagWithJavaDetails(config, providers);
        if (booleanProperty(settings, "org.gradle.caching", true)) {
            configureBuildCache(settings, config, micronautBuildSettingsExtension, providers, isCI);
        }
        var buildEnvironment = new BuildEnvironment(providers);
        if (Boolean.TRUE.equals(buildEnvironment.isTestSelectionEnabled().get())) {
            config.getBuildScan().tag("Predictive Test Selection");
        }
        captureSafeEnvironmentVariables(config);
    }

    private static void configureBuildCache(Settings settings, DevelocityConfiguration config, MicronautBuildSettingsExtension micronautBuildSettingsExtension, ProviderFactory providers, boolean isCI) {
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
                configureRemoteCache(config, buildCache, push, providers);
            }
        });
    }

    private static void configureRemoteCache(DevelocityConfiguration config, BuildCacheConfiguration buildCache, boolean push, ProviderFactory providers) {
        buildCache.remote(config.getBuildCache(), remote -> {
            remote.setEnabled(true);
            if (push) {
                String accessKey = providers.environmentVariable("GRADLE_ENTERPRISE_ACCESS_KEY")
                    .orElse(providers.environmentVariable("DEVELOCITY_ACCESS_KEY"))
                    // this one below is weird but for backward compatibility
                    .orElse(providers.systemProperty("GRADLE_ENTERPRISE_ACCESS_KEY"))
                    .getOrNull();
                if (accessKey != null && !accessKey.isEmpty()) {
                    remote.setPush(true);
                } else {
                    System.err.println("WARNING: Access key missing for remote build cache, cannot configure push!");
                }
            }
        });
    }

    private void tagWithJavaDetails(DevelocityConfiguration ge, ProviderFactory providers) {
        var vendor = providers.systemProperty("java.vendor");
        if (vendor.isPresent()) {
            ge.getBuildScan().tag("vendor:" + vendor.get().toLowerCase().replaceAll("\\W+", "_"));
        }
        ge.getBuildScan().tag("jdk:" + JavaVersion.current().getMajorVersion());
    }

    private void captureSafeEnvironmentVariables(DevelocityConfiguration ge) {
        for (var variable : SAFE_TO_LOG_ENV_VARIABLES) {
            String value = System.getenv(variable);
            ge.getBuildScan().value("env." + variable, value == null ? "<undefined>" : value);
        }
    }

    private void configureBuildScansPublishing(DevelocityConfiguration config,
                                               boolean isCI,
                                               boolean publishScanOnDemand,
                                               boolean buildScanExplicit) {
        config.getServer().convention("https://ge.micronaut.io");
        config.buildScan(buildScan -> {
            buildScan.getPublishing().onlyIf(context -> {
                if (publishScanOnDemand) {
                    return buildScanExplicit;
                }
                return isCI || context.isAuthenticated();
            });
        });
    }

    private static void applyGitHubActionsPlugin(Gradle gradle) {
        gradle.getRootProject().getPluginManager().apply(ActionsPlugin.class);
    }

}
