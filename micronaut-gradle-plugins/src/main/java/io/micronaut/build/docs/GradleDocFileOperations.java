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
package io.micronaut.build.docs;

import io.micronaut.docs.DocFileOperations;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.internal.file.FileOperations;

import java.io.File;

public final class GradleDocFileOperations implements DocFileOperations {
    private final FileOperations fileOperations;

    public GradleDocFileOperations(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void mkdir(File directory) {
        fileOperations.mkdir(directory);
    }

    @Override
    public void copy(File destinationDirectory, CopySource... sources) {
        fileOperations.copy(spec -> {
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
            spec.into(destinationDirectory);
            for (CopySource source : sources) {
                from(spec, source);
            }
        });
    }

    @Override
    public void delete(File file) {
        fileOperations.delete(file);
    }

    private static void from(CopySpec spec, CopySource source) {
        if (source == null || source.source() == null) {
            return;
        }
        if (source.hasIncludes()) {
            spec.from(source.source(), copySpec -> source.includes().forEach(copySpec::include));
        } else {
            spec.from(source.source());
        }
    }
}
