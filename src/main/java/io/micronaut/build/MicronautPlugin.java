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

import org.gradle.api.Plugin;

public interface MicronautPlugin<T> extends Plugin<T> {

    String TEST_SUITE_PROJECT_PREFIX = "test-suite";
    String MICRONAUT_PROJECT_PREFIX = "micronaut-";
    String BOM_PROJECT_SUFFIX = "-bom";
    String MICRONAUT_GROUP_ID = "io.micronaut";

    static String moduleNameOf(String projectName) {
        if (projectName.startsWith(MICRONAUT_PROJECT_PREFIX)) {
            return projectName;
        }
        return MICRONAUT_PROJECT_PREFIX + projectName;
    }
}
