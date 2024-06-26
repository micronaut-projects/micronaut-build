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
package io.micronaut.build.pom;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Nested;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extension to configure BOM projects.
 */
public interface MicronautBomExtension {
    /**
     * Specifies the name of the catalog to use.
     * Defaults to "libs"
     * @return the catalog to use
     */
    Property<String> getCatalogName();

    /**
     * Excludes projects from the BOM. Any call to this
     * method will override the default spec which excludes
     * modules containing the name "bom" or starting with
     * "test-suite".
     *
     * @return the exclusion spec
     */
    Property<Spec<? super Project>> getExcludeProject();

    /**
     * List of project names which shouldn't be included
     * in the BOM. This list is considered in addition to the
     * exclusion spec.
     *
     * @return the excluded projects
     */
    SetProperty<String> getExtraExcludedProjects();

    /**
     * If set to true, a Gradle version catalog will be published
     * alongside the BOM file.
     *
     * @return the catalog property
     */
    Property<Boolean> getPublishCatalog();

    /**
     * If set to true and that a version catalog is used in the project
     * declaring the BOM, then the BOM will automatically be generated
     * with elements of the version catalog.
     *
     * @return the project catalog import property
     */
    Property<Boolean> getImportProjectCatalog();

    /**
     * If set to true, the BOM will be added to the generated
     * version catalog.
     *
     * @return the include bom property
     */
    Property<Boolean> getIncludeBomInCatalog();

    /**
     * This property allows overriding the name of the properties
     * which will appear in the BOM, when they differ from the names
     * of properties as defined in the project version catalog.
     *
     * This property should only be used by legacy projects which
     * used old property names.
     *
     * @return the mapping from catalog property names to BOOM property names
     */
    MapProperty<String, String> getCatalogToPropertyNameOverrides();

    /**
     * Defines if imported catalogs (declared via the `bom.` prefix) should be
     * scanned for entries starting with "micronaut", in which case such
     * entries would be inlined into the current catalog (without version),
     * so that users get type-safe accessors without having to explicitly
     * import the nested catalog.
     *
     * @return the inline property
     */
    Property<Boolean> getInlineNestedCatalogs();

    /**
     * If set to true and that inline nested catalogs is set to true, then
     * whenever a catalog imports another BOM which is not published alongside
     * a version catalog, then the BOM will be inlined into the current catalog.
     * The aliases which are going to be used are the artifact ids, normalized
     * to catalog property names.
     *
     * @return the inline property
     */
    Property<Boolean> getInlineRegularBOMs();

    /**
     * Defines which aliases shouldn't be lined when importing other
     * catalogs. If empty, no aliases will be inlined except the ones
     * defined in {@link #getInlinedAliases()}. If an alias ends with
     * a wildcard, then all aliases starting with the prefix will be
     * excluded.
     * Behaves the same as adding `*` as the key in {@link #getExcludeFromInlining}
     *
     * @return the ignored aliases
     */
    SetProperty<String> getExcludedInlinedAliases();

    /**
     * Defines which aliases shouldn't be lined when importing other
     * catalogs. If empty, no aliases will be inlined except the ones
     * defined in {@link #getInlinedAliases()}. If an alias ends with
     * a wildcard, then all aliases starting with the prefix will be
     * excluded.
     * The key is the artifact id of the catalog/BOM which is inlined
     *
     * @return the ignored aliases
     */
    MapProperty<String, Set<String>> getExcludeFromInlining();

    /**
     * Defines which aliases should be lined when importing other
     * catalogs. If empty, all aliases will be inlined except the ones
     * defined in {@link #getExcludeFromInlining()}. If an alias ends
     * with a wildcard, then all aliases starting with the prefix will be
     * included.
     * The key is the artifact id of the catalog/BOM which is inlined
     *
     * @return the ignored aliases
     */
    MapProperty<String, Set<String>> getInlinedAliases();

    /**
     * Defines the property name of the version of project dependencies,
     * in case it cannot be deduced properly from the root project name
     *
     * @return the property name
     */
    Property<String> getPropertyName();

    /**
     * Determines if the projects to include in the BOM are inferred,
     * in which case it would automatically include projects which
     * apply the publishing plugin.
     *
     * @return the infer property
     */
    Property<Boolean> getInferProjectsToInclude();

    /**
     * Allows setting a list of modules (group:artifact) which are going to be
     * managed, but for which the version must be inferred from what is in the
     * catalog. This can be useful when we know a dependency will be used, but
     * it's not managed by any BOM we include, but it still appears in the
     * dependency graph.
     *
     * The key is `groupId:artifactId` project coordinates and the value is
     * an alias (without `managed-`) to be included.
     */
    MapProperty<String, String> getInferredManagedDependencies();

    default void inferredManagedDependencies(List<String> dependencies) {
        getInferredManagedDependencies().putAll(dependencies.stream().collect(Collectors.toMap(
            d -> d,
            d -> d.substring(d.indexOf(":") + 1)
        )));
    }

    @Nested
    BomSuppressions getSuppressions();

    default void suppressions(Action<? super BomSuppressions> spec) {
        spec.execute(getSuppressions());
    }

    default void excludeFromInlining(String alias, String... dependencies) {
        getExcludeFromInlining().put(alias, Set.of(dependencies));
    }

    default void excludeFromInlining(String alias, Set<String> dependencies) {
        getExcludeFromInlining().put(alias, dependencies);
    }
}
