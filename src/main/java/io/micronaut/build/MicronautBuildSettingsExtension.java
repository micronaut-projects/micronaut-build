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

import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser;
import io.micronaut.build.catalogs.internal.VersionModel;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

public abstract class MicronautBuildSettingsExtension {

    abstract Property<Boolean> getUseLocalCache();

    abstract Property<Boolean> getUseRemoteCache();

    @Inject
    protected abstract ProviderFactory getProviders();

    private final Settings settings;

    @Inject
    public MicronautBuildSettingsExtension(ProviderFactory providers, Settings settings) {
        this.settings = settings;
        getUseLocalCache().convention(booleanProvider(providers, "localCache", true));
        getUseRemoteCache().convention(booleanProvider(providers, "remoteCache", true));
    }

    /**
     * Exposes the Micronaut version catalog so that
     * it can be used in modules using type safe accessors.
     * Importing the catalog will implicitly add the shared repositories.
     */
    public void importMicronautCatalog() {
        // Because we're a settings plugin, the "libs" version catalog
        // isn't available yet. So we have to parse it ourselves to find
        // the micronaut version!
        File versionCatalog = new File(settings.getRootDir(), "gradle/libs.versions.toml");
        Optional<String> micronautVersion = Optional.empty();
        if (versionCatalog.exists()) {
            LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
            try (InputStream in = Files.newInputStream(versionCatalog.toPath())) {
                parser.parse(in);
                Optional<VersionModel> micronaut = parser.getModel().findVersion("micronaut");
                if (micronaut.isPresent()) {
                    micronautVersion = Optional.ofNullable(micronaut.get().getVersion().getRequire());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!micronautVersion.isPresent()) {
            micronautVersion = Optional.ofNullable(getProviders().gradleProperty("micronautVersion").getOrNull());
        }
        if (micronautVersion.isPresent()) {
            String version = micronautVersion.get();
            settings.dependencyResolutionManagement(mgmt -> {
                mgmt.getRepositoriesMode().set(RepositoriesMode.PREFER_PROJECT);
                mgmt.repositories(RepositoryHandler::mavenCentral);
                mgmt.getVersionCatalogs().create("mn", catalog -> catalog.from("io.micronaut:micronaut-bom:" + version));
            });
        }
    }

    static Provider<Boolean> booleanProvider(ProviderFactory providers, String gradleProperty, boolean defaultValue) {
        return providers.gradleProperty(gradleProperty).forUseAtConfigurationTime().map(Boolean::parseBoolean).orElse(defaultValue);
    }
}
