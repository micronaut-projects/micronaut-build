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
package io.micronaut.docs;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public interface DocFileOperations {

    void mkdir(File directory);

    void copy(File destinationDirectory, CopySource... sources);

    void delete(File file);

    record CopySource(File source, Set<String> includes) {
        public CopySource {
            includes = includes == null ? Set.of() : Set.copyOf(includes);
        }

        public static CopySource of(File source) {
            return new CopySource(source, Set.of());
        }

        public static CopySource including(File source, String... includes) {
            return new CopySource(source, Arrays.stream(includes).collect(Collectors.toUnmodifiableSet()));
        }

        public boolean hasIncludes() {
            return !includes.isEmpty();
        }
    }
}
