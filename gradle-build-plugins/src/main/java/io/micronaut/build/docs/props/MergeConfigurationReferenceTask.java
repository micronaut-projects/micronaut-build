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
package io.micronaut.build.docs.props;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
public abstract class MergeConfigurationReferenceTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getInputFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    protected void merge() throws FileNotFoundException, UnsupportedEncodingException {
        Set<File> inputFiles = getInputFiles().getAsFileTree().getFiles();
        File outputFile = getOutputFile().getAsFile().get();
        File parentFile = outputFile.getParentFile();
        parentFile.mkdirs();
        try (PrintWriter prn = new PrintWriter(outputFile, StandardCharsets.UTF_8.name())) {
            inputFiles.stream()
                    .sorted(Comparator.comparing(File::getName))
                    .forEachOrdered(file -> {
                        String header = "=== " +
                                Arrays.stream(file.getName().replace(".adoc", "").split("-"))
                                        .map(token -> Character.toUpperCase(token.charAt(0)) + token.substring(1))
                                        .collect(Collectors.joining(" "));
                        prn.print(header);
                        prn.print('\n');
                        try {
                            Files.readAllLines(file.toPath())
                                    .forEach(line -> {
                                        prn.print(line);
                                        prn.print('\n');
                                    });
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        prn.write('\n');
                    });
        }
    }
}
