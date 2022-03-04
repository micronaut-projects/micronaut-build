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
        catalogExtension.versionCatalog {builder ->
            Set<String> knownAliases = []
            extraVersions.forEach { alias, version ->
                builder.version(alias, version)
            }
            extraLibraries.forEach { alias, library ->
                knownAliases.add(alias)
                builder.library(alias, library.group, library.name)
                        .versionRef(library.versionRef)
            }
            model.versionsTable.each { version ->
                if (version.reference.startsWith('managed-')) {
                    builder.version(version.reference.substring(8), version.version.require)
                }
            }
            model.librariesTable.each { library ->
                if (library.alias.startsWith("managed-") && library.version.reference) {
                    if (!library.version.reference.startsWith("managed-")) {
                        throw new InvalidUserCodeException("Version catalog declares a managed library '${library.alias}' referencing a non managed version '${library.version.reference}'. Make sure to use a managed version.")
                    }

                    def alias = library.alias.substring(8)
                    knownAliases.add(alias)
                    builder.library(alias, library.group, library.name)
                            .versionRef(library.version.reference.substring(8))
                }
            }
            afterBuildingModel.each {
                it.accept(new BuilderState(builder, knownAliases))
            }
        }
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
        final Set<String> knownAliases
    }
}
