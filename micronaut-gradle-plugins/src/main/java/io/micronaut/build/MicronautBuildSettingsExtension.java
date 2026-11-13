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

import io.micronaut.build.catalogs.internal.BomCatalogCoordinatesSource;
import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser;
import io.micronaut.build.catalogs.internal.RichVersion;
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel;
import io.micronaut.build.catalogs.internal.VersionModel;
import io.micronaut.build.utils.DefaultVersions;
import io.micronaut.build.utils.IncludedBuildSupport;
import me.champeau.gradle.igp.GitIncludeExtension;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.micronaut.build.BomSupport.coreBomArtifactId;
import static io.micronaut.build.MicronautPlugin.BOM_PROJECT_SUFFIX;
import static io.micronaut.build.MicronautPlugin.MICRONAUT_PROJECT_PREFIX;
import static io.micronaut.build.MicronautPlugin.TEST_SUITE_PROJECT_PREFIX;

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
     *
     * @return the standardized project names property
     */
    public abstract Property<Boolean> getUseStandardizedProjectNames();

    /**
     * Configures a list of project path prefixes which correspond to projects which do
     * not use the "standard" project naming convention (e.g `examples:`).
     *
     * @return the list of non-standard project path prefixes
     */
    public abstract ListProperty<String> getNonStandardProjectPathPrefixes();

    /**
     * Configures a list of project name prefixes which correspond to projects which do
     * not use the "standard" project naming convention (e.g `test-suite-`).
     *
     * @return the list of non-standard project path prefixes
     */
    public abstract ListProperty<String> getNonStandardProjectNamePrefixes();

    /**
     * Configures root-relative directories whose {@code gradle.properties} should
     * receive the current Micronaut Build version for included build dependency wiring.
     *
     * @return the list of directories
     */
    public abstract ListProperty<String> getMicronautBuildVersionDirectories();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ExecOperations getExecOperations();

    private final AtomicBoolean repositoriesAdded = new AtomicBoolean();
    private final Set<String> includedBoms = new HashSet<>();
    private final Settings settings;
    private final String micronautVersion;
    private final String micronautTestVersion;
    private final String micronautLoggingVersion;
    private final VersionCatalogTomlModel versionCatalogTomlModel;
    private Configuration coreCatalog;
    private Configuration platformCatalog;

    @Inject
    public MicronautBuildSettingsExtension(ProviderFactory providers, Settings settings) {
        this.settings = settings;
        getUseLocalCache().convention(booleanProvider(providers, "localCache", true));
        getUseRemoteCache().convention(booleanProvider(providers, "remoteCache", true));
        getUseStandardizedProjectNames().convention(false);
        getNonStandardProjectPathPrefixes().set(Collections.singletonList(":examples:"));
        getNonStandardProjectNamePrefixes().set(Arrays.asList(MICRONAUT_PROJECT_PREFIX, TEST_SUITE_PROJECT_PREFIX));
        getMicronautBuildVersionDirectories().convention(Arrays.asList("buildSrc", "build-logic"));
        this.versionCatalogTomlModel = loadVersionCatalogTomlModel();
        this.micronautVersion = determineMicronautVersion();
        this.micronautTestVersion = determineMicronautTestVersion();
        this.micronautLoggingVersion = determineMicronautLoggingVersion();
        String platformVersion = determineMicronautVersion("micronaut-platform", null);
        if (platformVersion != null) {
            includeImportedBom("io.micronaut.platform:micronaut-platform:" + platformVersion);
        }
        settings.getDependencyResolutionManagement().getVersionCatalogs().configureEach(catalog -> {
            var prefix = "override." + catalog.getName() + ".";
            var provider = providers.gradlePropertiesPrefixedBy(prefix);
            if (provider.isPresent()) {
                for (Map.Entry<String, String> entry : provider.get().entrySet()) {
                    var key = entry.getKey().substring(prefix.length());
                    var version = entry.getValue();
                    catalog.version(key, version);
                    LOGGER.info("Overriding {} version to {}", key, version);
                }
            }
            var overrideFile = providers.gradleProperty("override.file." + catalog.getName());
            if (overrideFile.isPresent()) {
                try {
                    List<String> overrides = Files.readAllLines(Path.of(overrideFile.get()));
                    for (String override : overrides) {
                        var entry = override.split("=");
                        if (entry.length == 2) {
                            var key = entry[0].trim();
                            var version = entry[1].trim();
                            catalog.version(key, version);
                            LOGGER.info("Overriding {} version to {}", key, version);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private VersionCatalogTomlModel loadVersionCatalogTomlModel() {
        // Because we're a settings plugin, the "libs" version catalog
        // isn't available yet. So we have to parse it ourselves to find
        // the micronaut version!
        File catalogsDir = new File(settings.getRootDir(), "gradle");
        if (catalogsDir.exists()) {
            LenientVersionCatalogParser parser = new LenientVersionCatalogParser(true);
            Arrays.stream(catalogsDir.listFiles())
                .filter(f -> f.getName().endsWith(".versions.toml"))
                .forEach(f -> {
                    try (InputStream in = Files.newInputStream(f.toPath())) {
                        parser.parse(in);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            return parser.getModel();
        }
        return null;
    }

    public String getMicronautVersion() {
        return micronautVersion;
    }

    private String determineMicronautVersion() {
        return determineMicronautVersion("micronaut", null);
    }

    private String determineMicronautTestVersion() {
        return determineMicronautVersion("micronaut-test", DefaultVersions.MICRONAUT_TEST_VERSION);
    }

    private String determineMicronautLoggingVersion() {
        return determineMicronautVersion("micronaut-logging", DefaultVersions.MICRONAUT_LOGGING_VERSION);
    }

    private String determineMicronautVersion(String moduleNameKebabCase, String defaultVersion) {
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
        return micronautVersion.orElse(defaultVersion);
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
        if (micronautLoggingVersion != null) {
            settings.getGradle().settingsEvaluated(unused -> {
                settings.dependencyResolutionManagement(mgmt -> {
                    configureRepositories(mgmt);
                    if (mgmt.getVersionCatalogs().findByName("mnLogging") == null) {
                        mgmt.getVersionCatalogs().create("mnLogging", catalog -> catalog.from("io.micronaut.logging:micronaut-logging-bom:" + micronautLoggingVersion));
                    } else {
                        LOGGER.warn("Version catalog 'mnLogging' can be automatically imported. You can remove it from settings.gradle(.kts) file.");
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
            if (isMicronautPlatform(groupId, artifactId) && determineMicronautVersion("micronaut-platform", null) == null) {
                throw new IllegalStateException("The platform must be imported using importMicronautCatalog(alias, true).");
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
               (("micronaut-platform".equals(groupId) || "io.micronaut.platform".equals(groupId)) && "micronaut-platform".equals(artifactId));
    }

    /**
     * Exposes a Micronaut version catalog so that
     * it can be used in modules using type safe accessors.
     * Importing the catalog will implicitly add the shared repositories.
     * The name of the catalog is derived from the artifact id in the GAV
     * coordinates. For example, if the artifact id is "micronaut-aws-bom",
     * then the derived catalog name will be "mnAws".
     * If the local library has no version declaration, its version is looked up
     * by Maven coordinates in the platform catalog when {@code micronaut-platform}
     * is configured, followed by the catalog published with the configured core BOM.
     * Explicit version declarations retain their existing interpretation.
     * This overload only imports the catalog; additional BOM inclusion is opt-in.
     */
    public void importMicronautCatalog(String alias) {
        importMicronautCatalog(alias, false);
    }

    /**
     * Imports a Micronaut catalog, optionally applying its BOM through the existing
     * {@code globalBoms} or application {@code micronautBoms} configuration in
     * projects using the maintainer build extension. Java projects without that
     * extension receive it and a {@code globalBoms} bucket. These provide implementation and
     * annotation-processor constraints.
     * The project's {@code enableBom} setting also controls these BOMs.
     *
     * @param alias the local library alias identifying the catalog
     * @param includeBOM whether to include the imported BOM in dependency management
     */
    public void importMicronautCatalog(String alias, boolean includeBOM) {
        settings.dependencyResolutionManagement(mgmt -> {
            configureRepositories(mgmt);
            String gavCoordinates = versionCatalogTomlModel.getLibrariesTable()
                .stream()
                .filter(lib -> lib.getAlias().equals(alias))
                .findFirst()
                .map(library -> {
                    if (library.getVersion().getReference() == null && library.getVersion().getVersion() == RichVersion.EMPTY) {
                        return inferredCatalogCoordinates(library.getGroup(), library.getName(), alias);
                    }
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
            if (isMicronautPlatform(groupId, artifactId) && !includeBOM && determineMicronautVersion("micronaut-platform", null) == null) {
                throw new IllegalStateException("The platform must be imported using importMicronautCatalog(alias, true).");
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
                if (includeBOM) {
                    includeImportedBom(gavCoordinates);
                }
            } else {
                throw new IllegalStateException("Invalid version catalog GAV coordinates: " + gavCoordinates + ". Expected format: group:artifact:version");
            }
        });
    }

    private void includeImportedBom(String coordinates) {
        if (!includedBoms.add(coordinates)) {
            return;
        }
        settings.getGradle().beforeProject(project -> {
            project.getPluginManager().withPlugin("java", javaPlugin -> {
                project.getPluginManager().apply(MicronautBuildExtensionPlugin.class);
                var boms = project.getConfigurations().maybeCreate("globalBoms");
                boms.setCanBeResolved(false);
                boms.setCanBeConsumed(false);
                for (String name : Arrays.asList("implementation", "annotationProcessor", "testAnnotationProcessor")) {
                    project.getConfigurations().getByName(name).extendsFrom(boms);
                }
            });
            project.getPlugins().withType(MicronautBuildExtensionPlugin.class).configureEach(unused -> {
                project.getConfigurations().matching(configuration -> configuration.getName().equals("globalBoms") ||
                        configuration.getName().equals("micronautBoms"))
                    .configureEach(configuration -> {
                        var extension = project.getExtensions().getByType(MicronautBuildExtension.class);
                        configuration.getDependencies().addAllLater(extension.getEnableBom().map(enabled ->
                            enabled ? Collections.singletonList(project.getDependencies().platform(coordinates)) : Collections.emptyList()
                        ));
                    });
            });
        });
    }

    private String inferredCatalogCoordinates(String group, String artifact, String alias) {
        String platformVersion = determineMicronautVersion("micronaut-platform", null);
        if (platformVersion != null) {
            if (platformCatalog == null) {
                platformCatalog = catalogConfiguration("io.micronaut.platform:micronaut-platform:" + platformVersion);
            }
            String coordinates = catalogCoordinates(platformCatalog, group, artifact).getOrNull();
            if (coordinates != null) {
                return coordinates;
            }
        }
        if (micronautVersion != null) {
            if (coreCatalog == null) {
                coreCatalog = catalogConfiguration("io.micronaut:" + coreBomArtifactId(micronautVersion) + ":" + micronautVersion);
            }
            String coordinates = catalogCoordinates(coreCatalog, group, artifact).getOrNull();
            if (coordinates != null) {
                return coordinates;
            }
        }
        if (micronautVersion == null && platformVersion == null) {
            throw new IllegalStateException("No version for catalog '" + alias +
                "'. Declare its version or configure [versions].micronaut or micronautVersion, " +
                "or [versions].micronaut-platform or micronautPlatformVersion.");
        }
        String searched = platformVersion == null ? "Micronaut core BOM " + micronautVersion :
            "Micronaut platform BOM " + platformVersion + (micronautVersion == null ? "" : " and core BOM " + micronautVersion);
        String verb = micronautVersion != null && platformVersion != null ? " do not" : " does not";
        throw new IllegalStateException(searched + verb + " supply a catalog version for " + group + ":" + artifact +
            ". Declare an explicit local version.");
    }

    private Configuration catalogConfiguration(String coordinates) {
        var script = settings.getBuildscript();
        settings.getDependencyResolutionManagement().getRepositories().forEach(script.getRepositories()::add);
        Configuration configuration = script.getConfigurations().detachedConfiguration(script.getDependencies().create(coordinates + "@toml"));
        configuration.setTransitive(false);
        return configuration;
    }

    private Provider<String> catalogCoordinates(Configuration catalog, String group, String artifact) {
        return getProviders().of(BomCatalogCoordinatesSource.class, spec -> {
            spec.getParameters().getCatalog().from(catalog);
            spec.getParameters().getGroup().set(group);
            spec.getParameters().getArtifact().set(artifact);
        });
    }

    /**
     * Convenience method for adding the snapshot repository globally.
     */
    public void addSnapshotRepository() {
        settings.dependencyResolutionManagement(mgmt -> {
            configureRepositories(mgmt);
            mgmt.repositories(repos -> repos.maven(repo -> repo.setUrl("https://central.sonatype.com/repository/maven-snapshots/")));
        });
    }

    static Provider<Boolean> booleanProvider(ProviderFactory providers, String gradleProperty, boolean defaultValue) {
        return providers.gradleProperty(gradleProperty).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    /**
     * Allows integrating an upstream Micronaut project as a dependency of this project,
     * as sources.
     *
     * @param githubProjectName the GitHub project name (without the micronaut-projects/ part, e.g "micronaut-core")
     * @param branch the branch to use
     */
    public void requiresDevelopmentVersion(String githubProjectName, String branch) {
        var gitIncludeExtension = settings.getExtensions().findByType(GitIncludeExtension.class);
        if (gitIncludeExtension != null) {
            gitIncludeExtension.include(githubProjectName, repo -> {
                repo.getUri().set("https://github.com/micronaut-projects/" + githubProjectName + ".git");
                repo.getBranch().set(branch);
                // The following is a workaround for a Gradle limitation: when we include a build
                // which generates a version catalog, then Gradle is not capable of figuring out
                // that the task which generates that version catalog has to be called so that the
                // consuming build can use it in its settings. This slows downs builds infortunately.
                repo.codeReady(evt -> IncludedBuildSupport.configureIncludedBuildCatalogPublication(getExecOperations(), evt, gitIncludeExtension, githubProjectName));
            });
        }
        // Disable sanity check which will fail when using development versions
        settings.getGradle().beforeProject(p -> {
            var extensions = p.getExtensions();
            if (extensions.findByName("disable.micronaut.version.check") == null) {
                extensions.add("disable.micronaut.version.check", true);
            }
        });
    }

}
