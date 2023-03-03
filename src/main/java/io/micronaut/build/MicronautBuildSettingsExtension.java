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
import io.micronaut.build.catalogs.internal.RichVersion;
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel;
import io.micronaut.build.catalogs.internal.VersionModel;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.micronaut.build.BomSupport.coreBomArtifactId;
import static io.micronaut.build.MicronautPlugin.BOM_PROJECT_SUFFIX;
import static io.micronaut.build.MicronautPlugin.MICRONAUT_PROJECT_PREFIX;

public abstract class MicronautBuildSettingsExtension {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicronautBuildSettingsExtension.class);

    public abstract Property<Boolean> getUseLocalCache();

    public abstract Property<Boolean> getUseRemoteCache();

    /**
     * Configures use of "standard" project names. When set to "true",
     * project names will be automatically configured to start with "micronaut-",
     * except for test suites.
     * This makes it cleaner for publications, since the project name will match
     * the publication artifact id, making it easier to use composite builds.
     * Defaults to false.
     * @return the standardized project names property
     */
    public abstract Property<Boolean> getUseStandardizedProjectNames();

    @Inject
    protected abstract ProviderFactory getProviders();

    private final AtomicBoolean repositoriesAdded = new AtomicBoolean();
    private final Settings settings;
    private final String micronautVersion;
    private final String micronautTestVersion;
    private final VersionCatalogTomlModel versionCatalogTomlModel;

    @Inject
    public MicronautBuildSettingsExtension(ProviderFactory providers, Settings settings) {
        this.settings = settings;
        getUseLocalCache().convention(booleanProvider(providers, "localCache", true));
        getUseRemoteCache().convention(booleanProvider(providers, "remoteCache", true));
        getUseStandardizedProjectNames().convention(false);
        this.versionCatalogTomlModel = loadVersionCatalogTomlModel();
        this.micronautVersion = determineMicronautVersion();
        this.micronautTestVersion = determineMicronautTestVersion();
    }

    private VersionCatalogTomlModel loadVersionCatalogTomlModel() {
        // Because we're a settings plugin, the "libs" version catalog
        // isn't available yet. So we have to parse it ourselves to find
        // the micronaut version!
        File versionCatalog = new File(settings.getRootDir(), "gradle/libs.versions.toml");
        if (versionCatalog.exists()) {
            LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
            try (InputStream in = Files.newInputStream(versionCatalog.toPath())) {
                parser.parse(in);
                return parser.getModel();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public String getMicronautVersion() {
        return micronautVersion;
    }

    private String determineMicronautVersion() {
        return determineMicronautVersion("micronaut");
    }

    private String determineMicronautTestVersion() {
        return determineMicronautVersion("micronaut-test");
    }

    private String determineMicronautVersion(String moduleNameKebabCase) {
        Optional<String> micronautVersion = Optional.empty();
        if (versionCatalogTomlModel != null) {
            Optional<VersionModel> micronaut = versionCatalogTomlModel.findVersion(moduleNameKebabCase);
            if (micronaut.isPresent()) {
                micronautVersion = Optional.ofNullable(micronaut.get().getVersion().getRequire());
            }
        }
        if (!micronautVersion.isPresent()) {
            String capitalizedName = moduleNameKebabCase.charAt(0) +
                                     Arrays.stream(moduleNameKebabCase.split("-"))
                                             .map(StringUtils::capitalize)
                                             .collect(Collectors.joining())
                                             .substring(1)
                                     + "Version";
            micronautVersion = Optional.ofNullable(getProviders().gradleProperty(capitalizedName).getOrNull());
        }
        return micronautVersion.orElse(null);
    }

    /**
     * Exposes the Micronaut version catalog so that
     * it can be used in modules using type safe accessors.
     * Importing the catalog will implicitly add the shared repositories.
     */
    public void importMicronautCatalog() {
        if (micronautVersion != null) {
            settings.dependencyResolutionManagement(mgmt -> {
                configureRepositories(mgmt);
                String artifactId = coreBomArtifactId(micronautVersion);
                mgmt.getVersionCatalogs().create("mn", catalog -> catalog.from("io.micronaut:" + artifactId + ":" + micronautVersion));
            });
        }
        if (micronautTestVersion != null) {
            settings.getGradle().settingsEvaluated(unused -> {
                settings.dependencyResolutionManagement(mgmt -> {
                    configureRepositories(mgmt);
                    if (mgmt.getVersionCatalogs().findByName("mnTest") == null) {
                        mgmt.getVersionCatalogs().create("mnTest", catalog -> catalog.from("io.micronaut.test:micronaut-test-bom:" + micronautTestVersion));
                    } else {
                        LOGGER.warn("Version catalog 'mnTest' can be automatically imported. You can remove it from settings.gradle(.kts) file.");
                    }
                });
            });
        }
    }

    private void configureRepositories(DependencyResolutionManagement mgmt) {
        if (repositoriesAdded.compareAndSet(false, true)) {
            mgmt.getRepositoriesMode().set(RepositoriesMode.PREFER_PROJECT);
            mgmt.repositories(RepositoryHandler::mavenCentral);
        }
    }

    /**
     * Exposes a Micronaut version catalog so that
     * it can be used in modules using type safe accessors.
     * Importing the catalog will implicitly add the shared repositories.
     * The name of the catalog is derived from the artifact id in the GAV
     * coordinates. For example, if the artifact id is "micronaut-aws-bom",
     * then the derived catalog name will be "mnAws".
     */
    public void importMicronautCatalogFromGAV(String gavCoordinates) {
        settings.dependencyResolutionManagement(mgmt -> {
            configureRepositories(mgmt);
            List<String> parts = Arrays.asList(gavCoordinates.split(":"));
            String groupId = parts.get(0);
            String artifactId = parts.get(1);
            if (isMicronautPlatform(groupId, artifactId)) {
                throw new IllegalStateException("Projects must not import the platform BOM or it would create a cyclic dependency. Please use the core BOM instead.");
            }
            if (parts.size() == 3) {
                String name = "mn";
                if (artifactId.startsWith(MICRONAUT_PROJECT_PREFIX)) {
                    artifactId = artifactId.substring(MICRONAUT_PROJECT_PREFIX.length());
                }
                if (artifactId.endsWith(BOM_PROJECT_SUFFIX)) {
                    artifactId = artifactId.substring(0, artifactId.length() - 4);
                }
                name += Arrays.stream(artifactId.split("-"))
                        .map(StringUtils::capitalize)
                        .collect(Collectors.joining());
                mgmt.getVersionCatalogs().create(name, catalog -> catalog.from(gavCoordinates));
            } else {
                throw new IllegalStateException("Invalid version catalog GAV coordinates: " + gavCoordinates + ". Expected format: group:artifact:version");
            }
        });
    }

    private static boolean isMicronautPlatform(String groupId, String artifactId) {
        return ("micronaut-bom".equals(groupId) && "micronaut-bom".equals(artifactId)) ||
               ("micronaut-platform".equals(groupId) && "micronaut-platform".equals(artifactId));
    }

    /**
     * Exposes a Micronaut version catalog so that
     * it can be used in modules using type safe accessors.
     * Importing the catalog will implicitly add the shared repositories.
     * The name of the catalog is derived from the artifact id in the GAV
     * coordinates. For example, if the artifact id is "micronaut-aws-bom",
     * then the derived catalog name will be "mnAws".
     */
    public void importMicronautCatalog(String alias) {
        settings.dependencyResolutionManagement(mgmt -> {
            configureRepositories(mgmt);
            String gavCoordinates = versionCatalogTomlModel.getLibrariesTable()
                    .stream()
                    .filter(lib -> lib.getAlias().equals(alias))
                    .findFirst()
                    .map(library -> {
                        String version;
                        if (library.getVersion().getReference() != null) {
                            version = versionCatalogTomlModel.findVersion(library.getVersion().getReference())
                                    .map(VersionModel::getVersion)
                                    .map(RichVersion::getRequire)
                                    .orElse(null);
                        } else {
                            version = library.getVersion().getVersion().getRequire();
                        }
                        return library.getGroup() + ":" + library.getName() + ":" + version;
                    })
                    .orElseThrow(() -> new IllegalStateException("Version catalog doesn't contain a library with alias: " + alias));
            List<String> parts = Arrays.asList(gavCoordinates.split(":"));
            String groupId = parts.get(0);
            String artifactId = parts.get(1);
            if (isMicronautPlatform(groupId, artifactId)) {
                throw new IllegalStateException("Projects must not import the platform BOM or it would create a cyclic dependency. Please use the core BOM instead.");
            }
            if (parts.size() == 3) {
                String name = "mn";
                if (artifactId.startsWith(MICRONAUT_PROJECT_PREFIX)) {
                    artifactId = artifactId.substring(MICRONAUT_PROJECT_PREFIX.length());
                }
                if (artifactId.endsWith(BOM_PROJECT_SUFFIX)) {
                    artifactId = artifactId.substring(0, artifactId.length() - 4);
                }
                name += Arrays.stream(artifactId.split("-"))
                        .map(StringUtils::capitalize)
                        .collect(Collectors.joining());
                mgmt.getVersionCatalogs().create(name, catalog -> catalog.from(gavCoordinates));
            } else {
                throw new IllegalStateException("Invalid version catalog GAV coordinates: " + gavCoordinates + ". Expected format: group:artifact:version");
            }
        });
    }

    /**
     * Convenience method for adding the snapshot repository globally.
     */
    public void addSnapshotRepository() {
        settings.dependencyResolutionManagement(mgmt -> {
            configureRepositories(mgmt);
            mgmt.repositories(repos -> repos.maven(repo -> repo.setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots")));
        });
    }

    static Provider<Boolean> booleanProvider(ProviderFactory providers, String gradleProperty, boolean defaultValue) {
        return providers.gradleProperty(gradleProperty).map(Boolean::parseBoolean).orElse(defaultValue);
    }
}
