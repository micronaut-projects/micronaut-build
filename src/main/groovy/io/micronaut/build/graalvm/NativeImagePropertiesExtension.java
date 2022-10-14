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

/**
 * Lets the user configure generation of a native-image.properties
 * file.
 */
public interface NativeImagePropertiesExtension {
    /**
     * Determines if native-image.properties file should be generated.
     * @return true if the file should be generated
     */
    Property<Boolean> getEnabled();

    /**
     * The content of the native-image.properties file to generate.
     * This cannot be used if any of the other convenience properties are used.
     * @return the contents of the native image properties file
     */
    Property<String> getContents();

    /**
     * The list of classes/packages to initialize at runtime
     * @return the list of classes/packages to initialize at runtime
     */
    ListProperty<String> getInitializeAtRuntime();

    /**
     * The list of classes/packages to initialize at build time
     * @return the list of classes/packages to initialize at build time
     */
    ListProperty<String> getInitializeAtBuildtime();

    /**
     * The list of features to enable
     * @return the list of features
     */
    ListProperty<String> getFeatures();
}
