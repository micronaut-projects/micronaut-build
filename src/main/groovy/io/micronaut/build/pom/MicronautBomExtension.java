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

/**
 * Extension to configure BOM projects.
 */
public interface MicronautBomExtension {
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
     * @return the catalog property
     */
    Property<Boolean> getPublishCatalog();

    /**
     * If set to true and that a version catalog is used in the project
     * declaring the BOM, then the BOM will automatically be generated
     * with elements of the version catalog.
     * @return the project catalog import property
     */
    Property<Boolean> getImportProjectCatalog();

    /**
     * If set to true, the BOM will be added to the generated
     * version catalog.
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
     * @return the inline property
     */
    Property<Boolean> getInlineNestedCatalogs();

    /**
     * Defines which aliases shouldn't be lined when importing other
     * catalogs
     * @return the ignored aliases
     */
    SetProperty<String> getExcludedInlinedAliases();

    /**
     * Defines the property name of the version of project dependencies,
     * in case it cannot be deduced properly from the root project name
     * @return the property name
     */
    Property<String> getPropertyName();

    @Nested
    BomSuppressions getSuppressions();

    default void suppressions(Action<? super BomSuppressions> spec) {
        spec.execute(getSuppressions());
    }
}
