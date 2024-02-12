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
import com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension;
import com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin;
import com.gradle.scan.plugin.BuildScanExtension;
import org.gradle.api.JavaVersion;
import io.micronaut.build.utils.ProviderUtils;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.nosphere.gradle.github.ActionsPlugin;

import java.lang.reflect.InvocationTargetException;

import static io.micronaut.build.BuildEnvironment.PREDICTIVE_TEST_SELECTION_ENV_VAR;

public class MicronautGradleEnterprisePlugin implements Plugin<Settings> {
    private static final String[] SAFE_TO_LOG_ENV_VARIABLES = new String[] {
            PREDICTIVE_TEST_SELECTION_ENV_VAR
    };

    @Override
    public void apply(Settings settings) {
        PluginManager pluginManager = settings.getPluginManager();
        pluginManager.apply(MicronautBuildSettingsPlugin.class);
        pluginManager.apply(GradleEnterprisePlugin.class);
        pluginManager.apply(CommonCustomUserDataGradlePlugin.class);
        Provider<String> projectGroup = settings.getProviders().gradleProperty("projectGroup");
        if (projectGroup.isPresent() && !projectGroup.get().startsWith("io.micronaut")) {
            // Do not apply the Gradle Enterprise plugin if the project doesn't belong to
            // our own group
            return;
        }
        GradleEnterpriseExtension ge = settings.getExtensions().getByType(GradleEnterpriseExtension.class);
        MicronautBuildSettingsExtension micronautBuildSettingsExtension = settings.getExtensions().getByType(MicronautBuildSettingsExtension.class);
        configureGradleEnterprise(settings, ge, micronautBuildSettingsExtension);
    }

    private void configureGradleEnterprise(Settings settings,
                                           GradleEnterpriseExtension ge,
                                           MicronautBuildSettingsExtension micronautBuildSettingsExtension) {
        ProviderFactory providers = settings.getProviders();
        Provider<Boolean> publishScanOnDemand = providers.gradleProperty("publishScanOnDemand")
                .map(Boolean::parseBoolean)
                .orElse(false);
        boolean isCI = ProviderUtils.guessCI(providers);
        configureBuildScansPublishing(ge, isCI, publishScanOnDemand);
        settings.getGradle().projectsLoaded(MicronautGradleEnterprisePlugin::applyGitHubActionsPlugin);
        tagWithJavaDetails(ge, providers);
        if (providers.gradleProperty("org.gradle.caching").map(Boolean::parseBoolean).orElse(true).get()) {
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
                    buildCache.remote(ge.getBuildCache(), remote -> {
                        remote.setEnabled(true);
                        if (push) {
                            String accessKey = providers.systemProperty("GRADLE_ENTERPRISE_ACCESS_KEY").getOrNull();
                            if (accessKey != null && !accessKey.isEmpty()) {
                                remote.setPush(true);
                            } else {
                                System.err.println("WARNING: Access key missing for remote build cache, cannot configure push!");
                            }
                        }
                    });
                }
            });
            BuildEnvironment buildEnvironment = new BuildEnvironment(providers);
            if (Boolean.TRUE.equals(buildEnvironment.isTestSelectionEnabled().get())) {
                ge.getBuildScan().tag("Predictive Test Selection");
            }
            captureSafeEnvironmentVariables(ge);
        }
    }

    private void tagWithJavaDetails(GradleEnterpriseExtension ge, ProviderFactory providers) {
        Provider<String> vendor = providers.systemProperty("java.vendor");
        if (vendor.isPresent()) {
            ge.getBuildScan().tag("vendor:" + vendor.get().toLowerCase().replaceAll("\\W+", "_"));
        }
        ge.getBuildScan().tag("jdk:" + JavaVersion.current().getMajorVersion());
    }

    private void captureSafeEnvironmentVariables(GradleEnterpriseExtension ge) {
        for (String variable : SAFE_TO_LOG_ENV_VARIABLES) {
            String value = System.getenv(variable);
            ge.getBuildScan().value("env." + variable, value == null ? "<undefined>" : value);
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

}
