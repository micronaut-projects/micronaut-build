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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@CacheableTask
public abstract class ProcessConfigPropsTask extends DefaultTask {
    private static final String SEPARATOR = "<<<";
    private static final String ID = "id=\"";
    private static final String ANCHOR_WITH_ID = "<a " + ID;
    private static final String DOUBLE_QUOTE = "\"";

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInputFiles();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileOperations getFileOperations();

    @TaskAction
    void process() throws IOException {
        for (File configProperties : getInputFiles().getFiles()) {
            if (configProperties.exists()) {
                File individualConfigsPropsFolder = getOutputDirectory().getAsFile().get();
                getFileOperations().delete(individualConfigsPropsFolder);
                getFileOperations().mkdir(individualConfigsPropsFolder);
                List<String> accumulator = new ArrayList<>();
                String configurationPropertyName = "";
                for (String line : Files.readAllLines(configProperties.toPath(), StandardCharsets.UTF_8)) {
                    if (line.startsWith(ANCHOR_WITH_ID)) {
                        String sub = line.substring(line.indexOf(ID) + ID.length());
                        sub = sub.substring(0, sub.indexOf(DOUBLE_QUOTE));
                        configurationPropertyName = sub;
                    }
                    if (SEPARATOR.equals(line)) {
                        File outputfile = new File(individualConfigsPropsFolder, configurationPropertyName + ".adoc");
                        try (PrintWriter writer = new PrintWriter(outputfile, StandardCharsets.UTF_8.name())) {
                            for (String s : accumulator) {
                                writer.print(s);
                                writer.print('\n');
                            }
                        }
                        accumulator.clear();
                        configurationPropertyName = null;
                    } else {
                        accumulator.add(line);
                    }
                }
            }
        }
    }
}
