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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.util.Set;

@CacheableTask
public abstract class PrepareJavadocAggregationTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Input
    public abstract SetProperty<String> getIncludes();

    @Input
    public abstract SetProperty<String> getExcludes();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Inject
    public abstract FileOperations getFileOperations();

    @TaskAction
    public void prepareJavadocAggregation() {
        getFileOperations().sync(spec -> {
            spec.from(getSources(), file -> {
                Set<String> excludes = getExcludes().get();
                if (!excludes.isEmpty()) {
                    file.exclude(excludes);
                }
                Set<String> includes = getIncludes().get();
                if (!includes.isEmpty()) {
                    file.include(includes);
                }
            });
            spec.exclude(f -> f.getFile().isFile() && !f.getName().endsWith(".java"));
            spec.into(getOutputDir().getAsFile());
        });
    }

}
