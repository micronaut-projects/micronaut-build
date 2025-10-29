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
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * This task is responsible for creating the resources directory
 * which is used by the guide generation task. This resources
 * directory contains default resources exploded from the grails
 * jar and extra resources provided by the user.
 */
@CacheableTask
public abstract class PrepareDocResourcesTask extends DefaultTask {

    @Input
    public abstract Property<String> getResourceClasspathJarName();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResources();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @Inject
    protected abstract FileOperations getFileOperations();

    @TaskAction
    public void copy() throws IOException {
        String src = getResourceClasspathJarName().get();
        File outputDir = getOutputDirectory().getAsFile().get();
        File tmpJarFile = new File(getTemporaryDir(), src);
        // Can't unjar a file from within a JAR, so we copy it to
        // the destination directory first.
        try {
            URL url = getClass().getClassLoader().getResource(src);
            if (url != null) {
                try (InputStream input = url.openStream()) {
                    Files.copy(input, tmpJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            getArchiveOperations().zipTree(tmpJarFile).visit((FileVisitDetails details) -> {
                String relativePath = details.getRelativePath().getPathString();
                if (!relativePath.startsWith("META-INF")) {
                    File target = new File(outputDir, relativePath);
                    if (details.isDirectory()) {
                        getFileOperations().mkdir(target);
                    } else {
                        try {
                            Files.copy(details.getFile().toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
            getFileOperations().copy(spec -> {
                spec.into(outputDir);
                spec.from(getResources());
            });
        }
        finally {
            getFileOperations().delete(tmpJarFile);
        }
    }
}
