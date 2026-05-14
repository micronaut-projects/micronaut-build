/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.pom;

import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser;
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.plugins.catalog.CatalogPluginExtension;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * This is an internal task which responsibility is to parse
 * the Micronaut version catalog used internally, extract components
 * which belong to the BOM, in order to populate the version catalog
 * model for Gradle.
 *
 * In the end, this model is used to generate a version catalog in
 * addition to the bom. This will let users choose between importing
 * a BOM or using the Micronaut version catalog.
 *
 */
public class VersionCatalogConverter {
    public static final String MAIN_ALIASES_SOURCE = "BOM build file or main version catalog";

    private final File catalogFile;
    private final CatalogPluginExtension catalogExtension;
    private final Map<String, String> extraVersions = new LinkedHashMap<>();
    private final Map<String, Library> extraLibraries = new LinkedHashMap<>();
    private final List<Consumer<? super BuilderState>> afterBuildingModel = new ArrayList<>();
    private final List<Consumer<? super InterceptedVersionCatalogBuilder.LibraryDefinition>> onLibrary = new ArrayList<>();

    private VersionCatalogTomlModel model;

    public VersionCatalogConverter(File catalogFile, CatalogPluginExtension ext) {
        this.catalogFile = catalogFile;
        this.catalogExtension = ext;
    }

    public void onLibrary(Consumer<? super InterceptedVersionCatalogBuilder.LibraryDefinition> consumer) {
        onLibrary.add(consumer);
    }

    public void afterBuildingModel(Consumer<? super BuilderState> consumer) {
        afterBuildingModel.add(consumer);
    }

    public VersionCatalogTomlModel getModel() {
        if (model == null) {
            LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
            if (catalogFile.exists()) {
                try (var input = catalogFile.toURI().toURL().openStream()) {
                    parser.parse(input);
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to parse version catalog " + catalogFile, e);
                }
            }
            model = parser.getModel();
        }
        return model;
    }

    public void populateModel() {
        catalogExtension.versionCatalog(versionCatalogBuilder -> {
            InterceptedVersionCatalogBuilder builder = new InterceptedVersionCatalogBuilder(versionCatalogBuilder);
            onLibrary.forEach(builder::onLibrary);
            Set<String> knownAliases = new LinkedHashSet<>();
            Set<String> knownPluginAliases = new LinkedHashSet<>();
            Set<String> knownVersionAliases = new LinkedHashSet<>();
            extraVersions.forEach((alias, version) -> {
                knownVersionAliases.add(alias);
                builder.version(alias, version);
            });
            extraLibraries.forEach((alias, library) -> {
                knownAliases.add(alias);
                builder.library(alias, library.getGroup(), library.getName())
                    .versionRef(library.getVersionRef());
            });
            getModel().getVersionsTable().forEach(version -> {
                String reference = version.getReference();
                if (isManagedAlias(reference)) {
                    String alias = reference.substring(8);
                    builder.version(alias, version.getVersion().getRequire());
                }
            });
            getModel().getLibrariesTable().forEach(library -> {
                String libraryAlias = library.getAlias();
                String versionReference = library.getVersion().getReference();
                if ((isManagedAlias(libraryAlias) || isBomAlias(libraryAlias)) && versionReference != null) {
                    if (!isManagedAlias(versionReference)) {
                        throw new InvalidUserCodeException("Version catalog declares a managed library '" + libraryAlias
                                                           + "' referencing a non managed version '" + versionReference
                                                           + "'. Make sure to use a managed version.");
                    }
                    String alias = isBomAlias(libraryAlias) ? libraryAlias : libraryAlias.substring(libraryAlias.indexOf('-') + 1);
                    knownAliases.add(alias);
                    builder.library(alias, library.getGroup(), library.getName())
                        .versionRef(versionReference.substring(8));
                }
            });
            getModel().getPluginsTable().forEach(plugin -> {
                String pluginAlias = plugin.alias();
                if (isManagedAlias(pluginAlias)) {
                    String versionReference = plugin.version().getReference();
                    if (versionReference != null && !isManagedAlias(versionReference)) {
                        throw new InvalidUserCodeException("Version catalog declares a managed plugin '" + pluginAlias
                                                           + "' referencing a non managed version '" + versionReference
                                                           + "'. Make sure to use a managed version.");
                    }
                    String alias = pluginAlias.substring(pluginAlias.indexOf('-') + 1);
                    knownPluginAliases.add(alias);
                    if (versionReference != null) {
                        builder.plugin(alias, plugin.id()).versionRef(versionReference.substring(8));
                    } else {
                        builder.plugin(alias, plugin.id()).version(plugin.version().getVersion().getRequire());
                    }
                }
            });
            afterBuildingModel.forEach(consumer -> {
                BuilderState builderState = new BuilderState(builder);
                knownAliases.forEach(alias -> builderState.getKnownAliases().get(alias).addSource(MAIN_ALIASES_SOURCE));
                knownPluginAliases.forEach(alias -> builderState.getKnownPluginAliases().get(alias).addSource(MAIN_ALIASES_SOURCE));
                knownVersionAliases.forEach(alias -> builderState.getKnownVersionAliases().get(alias).addSource(MAIN_ALIASES_SOURCE));
                consumer.accept(builderState);
            });
        });
    }

    public File getCatalogFile() {
        return catalogFile;
    }

    public CatalogPluginExtension getCatalogExtension() {
        return catalogExtension;
    }

    public Map<String, String> getExtraVersions() {
        return extraVersions;
    }

    public Map<String, Library> getExtraLibraries() {
        return extraLibraries;
    }

    public List<Consumer<? super BuilderState>> getAfterBuildingModel() {
        return afterBuildingModel;
    }

    public List<Consumer<? super InterceptedVersionCatalogBuilder.LibraryDefinition>> getOnLibrary() {
        return onLibrary;
    }

    private static boolean isManagedAlias(String libraryAlias) {
        return libraryAlias != null && libraryAlias.startsWith("managed-");
    }

    private static boolean isBomAlias(String reference) {
        return reference != null && reference.startsWith("boms-") && !reference.startsWith("boms-micronaut-");
    }

    public static Library library(String group, String name, String versionRef) {
        return new Library(group, name, versionRef);
    }

    public static final class Library {
        private final String group;
        private final String name;
        private final String versionRef;

        public Library(String group, String name, String versionRef) {
            this.group = group;
            this.name = name;
            this.versionRef = versionRef;
        }

        public String getGroup() {
            return group;
        }

        public String getName() {
            return name;
        }

        public String getVersionRef() {
            return versionRef;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Library library)) {
                return false;
            }
            return Objects.equals(group, library.group)
                && Objects.equals(name, library.name)
                && Objects.equals(versionRef, library.versionRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, name, versionRef);
        }

        @Override
        public String toString() {
            return "VersionCatalogConverter.Library(group=" + group + ", name=" + name + ", versionRef=" + versionRef + ")";
        }
    }

    public static final class BuilderState {
        private final VersionCatalogBuilder builder;
        private final Map<String, AliasRecord> knownAliases = new DefaultingAliasMap();
        private final Map<String, AliasRecord> knownPluginAliases = new DefaultingAliasMap();
        private final Map<String, AliasRecord> knownVersionAliases = new DefaultingAliasMap();

        public BuilderState(VersionCatalogBuilder builder) {
            this.builder = builder;
        }

        public VersionCatalogBuilder getBuilder() {
            return builder;
        }

        public Map<String, AliasRecord> getKnownAliases() {
            return knownAliases;
        }

        public Map<String, AliasRecord> getKnownPluginAliases() {
            return knownPluginAliases;
        }

        public Map<String, AliasRecord> getKnownVersionAliases() {
            return knownVersionAliases;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BuilderState that)) {
                return false;
            }
            return Objects.equals(builder, that.builder)
                && Objects.equals(knownAliases, that.knownAliases)
                && Objects.equals(knownPluginAliases, that.knownPluginAliases)
                && Objects.equals(knownVersionAliases, that.knownVersionAliases);
        }

        @Override
        public int hashCode() {
            return Objects.hash(builder, knownAliases, knownPluginAliases, knownVersionAliases);
        }

        @Override
        public String toString() {
            return "VersionCatalogConverter.BuilderState(builder=" + builder
                + ", knownAliases=" + knownAliases
                + ", knownPluginAliases=" + knownPluginAliases
                + ", knownVersionAliases=" + knownVersionAliases + ")";
        }
    }

    public static final class AliasRecord {
        private final String alias;
        private final Set<String> sources = new LinkedHashSet<>();

        AliasRecord(String alias) {
            this.alias = alias;
        }

        public String getAlias() {
            return alias;
        }

        public void addSource(String source) {
            sources.add(source);
        }

        public Set<String> getSources() {
            return Collections.unmodifiableSet(sources);
        }

        @Override
        public String toString() {
            return alias;
        }
    }

    private static final class DefaultingAliasMap extends LinkedHashMap<String, AliasRecord> {
        @Override
        public AliasRecord get(Object key) {
            if (key instanceof String alias) {
                return computeIfAbsent(alias, AliasRecord::new);
            }
            return super.get(key);
        }
    }
}
