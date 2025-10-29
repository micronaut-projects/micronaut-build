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
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@CacheableTask
public abstract class ReplaceAtLinkTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInputFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void execute() throws IOException {
        File outputFile = getOutputFile().getAsFile().get();
        if (outputFile.getParentFile().isDirectory() || outputFile.getParentFile().mkdirs()) {
            for (File configPropertiesFile : getInputFiles().getFiles()) {
                if (configPropertiesFile.exists()) {
                    List<String> lines = Files.readAllLines(configPropertiesFile.toPath(), StandardCharsets.UTF_8);
                    try (PrintWriter prn = new PrintWriter(outputFile, StandardCharsets.UTF_8.name())) {
                        for (String line : lines) {
                            String processedLine = line;
                            while (processedLine.contains("{@link io.micronaut.")) {
                                processedLine = atLinkReplacer(processedLine);
                            }
                            prn.print(processedLine + "\n");
                        }
                    }
                }
            }

        }
    }

    private static String atLinkReplacer(String str) {
        String newLine = str.substring(0, str.indexOf("{@link io.micronaut."));
        String sub = "api:" + str.substring(str.indexOf("{@link io.micronaut.") + "{@link io.micronaut.".length());
        newLine += sub.substring(0, sub.indexOf('}')) + "[]";
        newLine += sub.substring(sub.indexOf('}') + "}".length());
        return newLine;
    }
}
