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
package io.micronaut.build.graalvm;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * Configures the GraalVM reachability metadata library list generated for releases.
 */
public interface GraalVmReachabilityMetadataExtension {
    /**
     * The minimum released version to report for generated entries.
     * @return the minimum version
     */
    Property<String> getMinimumVersion();

    /**
     * The workflow file name used for the default tests location.
     * @return the workflow file name
     */
    Property<String> getTestWorkflowName();

    /**
     * The test level to report for generated entries.
     * @return the test level
     */
    Property<String> getTestLevel();

    /**
     * Metadata locations to report for every generated entry.
     * @return the metadata locations
     */
    ListProperty<String> getMetadataLocations();

    /**
     * Test locations to report for every generated entry.
     * @return the test locations
     */
    ListProperty<String> getTestsLocations();

    /**
     * Additional project names that should not be included in the generated list.
     * @return excluded project names
     */
    SetProperty<String> getExcludedProjectNames();
}
