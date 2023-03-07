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

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;

import java.util.Set;

public interface BomSuppressions {
    /**
     * This property can be used to declare the set of g:a:v coordinates
     * of dependencies reported with problems in BOM checking, but that
     * we should ignore.
     * @return the silenced dependencies
     */
    @Input
    SetProperty<String> getDependencies();

    /**
     * This property can be used to define a set of group ids which are
     * allowed when a BOM defines a dependency on a group which is not
     * its own group.
     */
    @Input
    MapProperty<String, Set<String>> getBomAuthorizedGroupIds();

    /**
     * This property can be used to declare a set of version aliases which
     * were present in a previous version of the catalog, but are not in
     * the current version, in case it's a legitimate change.
     * @return the set of accepted regressions
     */
    @Input
    SetProperty<String> getAcceptedVersionRegressions();

    /**
     * This property can be used to declare a set of library aliases which
     * were present in a previous version of the catalog, but are not in
     * the current version, in case it's a legitimate change.
     * @return the set of accepted regressions
     */
    @Input
    SetProperty<String> getAcceptedLibraryRegressions();

}
