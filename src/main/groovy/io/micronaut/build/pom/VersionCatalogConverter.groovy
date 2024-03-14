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
package io.micronaut.build.pom

import groovy.transform.Canonical
import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.plugins.catalog.CatalogPluginExtension

import java.util.function.Consumer

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
@Canonical
class VersionCatalogConverter {
    public static final String MAIN_ALIASES_SOURCE = "BOM build file or main version catalog"

    final File catalogFile
    final CatalogPluginExtension catalogExtension
    final Map<String, String> extraVersions = [:]
    final Map<String, Library> extraLibraries = [:]
    final List<Consumer<? super BuilderState>> afterBuildingModel = []

    VersionCatalogConverter(File catalogFile, CatalogPluginExtension ext) {
        this.catalogFile = catalogFile
        this.catalogExtension = ext
    }

    private VersionCatalogTomlModel model

    void afterBuildingModel(Consumer<? super BuilderState> consumer) {
        afterBuildingModel << consumer
    }

    VersionCatalogTomlModel getModel() {
        if (model == null) {
            def parser = new LenientVersionCatalogParser()
            if (catalogFile.exists()) {
                parser.parse(catalogFile.newInputStream())
            }
            model = parser.model
        }
        model
    }

    void populateModel() {
        catalogExtension.versionCatalog { builder ->
            Set<String> knownAliases = []
            Set<String> knownPluginAliases = []
            Set<String> knwonVersionAliases = []
            extraVersions.forEach { alias, version ->
                knwonVersionAliases.add(alias)
                builder.version(alias, version)
            }
            extraLibraries.forEach { alias, library ->
                knownAliases.add(alias)
                builder.library(alias, library.group, library.name)
                        .versionRef(library.versionRef)
            }
            model.versionsTable.each { version ->
                String reference = version.reference
                if (isManagedAlias(reference)) {
                    def alias = reference.substring(8)
                    builder.version(alias, version.version.require)
                }
            }
            model.librariesTable.each { library ->
                String libraryAlias = library.alias
                if ((isManagedAlias(libraryAlias) || isBomAlias(libraryAlias)) && library.version.reference) {
                    if (!isManagedAlias(library.version.reference)) {
                        throw new InvalidUserCodeException("Version catalog declares a managed library '${libraryAlias}' referencing a non managed version '${library.version.reference}'. Make sure to use a managed version.")
                    }
                    def alias = isBomAlias(libraryAlias) ? libraryAlias : libraryAlias.substring(libraryAlias.indexOf('-') + 1)
                    knownAliases.add(alias)
                    builder.library(alias, library.group, library.name)
                            .versionRef(library.version.reference.substring(8))
                }
            }
            model.pluginsTable.each { plugin ->
                String pluginAlias = plugin.alias()
                if (isManagedAlias(pluginAlias)) {
                    if (plugin.version().reference && !isManagedAlias(plugin.version().reference)) {
                        throw new InvalidUserCodeException("Version catalog declares a managed plugin '${pluginAlias}' referencing a non managed version '${plugin.version().reference}'. Make sure to use a managed version.")
                    }
                    def alias = pluginAlias.substring(pluginAlias.indexOf('-') + 1)
                    knownPluginAliases.add(alias)
                    if (plugin.version().reference) {
                        builder.plugin(alias, plugin.id()).versionRef(plugin.version().reference.substring(8))
                    } else {
                        builder.plugin(alias, plugin.id()).version(plugin.version().version.require)
                    }
                }
            }
            afterBuildingModel.each {
                BuilderState builderState = new BuilderState(builder)
                knownAliases.each {
                    builderState.knownAliases.get(it).addSource(MAIN_ALIASES_SOURCE)
                }
                knownPluginAliases.each {
                    builderState.knownPluginAliases.get(it).addSource(MAIN_ALIASES_SOURCE)
                }
                knwonVersionAliases.each {
                    builderState.knownVersionAliases.get(it).addSource(MAIN_ALIASES_SOURCE);
                }
                it.accept(builderState)
            }
        }
    }

    private static boolean isManagedAlias(String libraryAlias) {
        libraryAlias?.startsWith("managed-")
    }

    private static boolean isBomAlias(String reference) {
        reference.startsWith('boms-') && !reference.startsWith("boms-micronaut-")
    }

    static Library library(String group, String name, String versionRef) {
        new Library(group, name, versionRef)
    }

    @Canonical
    static class Library {
        final String group
        final String name
        final String versionRef
    }

    @Canonical
    static class BuilderState {
        final VersionCatalogBuilder builder
        final Map<String, AliasRecord> knownAliases = [:].withDefault { String alias ->
            new AliasRecord(alias)
        }
        final Map<String, AliasRecord> knownPluginAliases = [:].withDefault { String alias ->
            new AliasRecord(alias)
        }
        final Map<String, AliasRecord> knownVersionAliases = [:].withDefault { String alias ->
            new AliasRecord(alias)
        }
    }

    static class AliasRecord {
        final String alias
        private final Set<String> sources = []

        AliasRecord(String alias) {
            this.alias = alias
        }

        void addSource(String source) {
            sources << source
        }

        Set<String> getSources() {
            Collections.unmodifiableSet(sources)
        }

        String toString() {
            alias
        }
    }
}
