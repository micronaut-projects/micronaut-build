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
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.build.utils.ConsoleUtils.clickableUrl;

@CacheableTask
public abstract class ValidateAsciidocOutputTask extends DefaultTask {
    private final static String UNRESOLVED_DIRECTIVE = "Unresolved directive in";

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getInputDirectory();

    @Input
    @Optional
    public abstract Property<Boolean> getFailOnError();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    void validate() throws IOException {
        final Map<String, List<String>> errors = new HashMap<>();
        getInputDirectory().getAsFileTree().visit(details -> {
            if (details.getName().endsWith(".html")) {
                try {
                    Files.readAllLines(details.getFile().toPath()).forEach(line -> {
                        if (line.contains(UNRESOLVED_DIRECTIVE)) {
                            errors.computeIfAbsent(details.getName(), k -> new ArrayList<>()).add(line);
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        if (!errors.isEmpty()) {
            try (PrintWriter prn = new PrintWriter(new FileWriter(getReport().getAsFile().get()))) {
                for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                    prn.println("In file " + entry.getKey());
                    for (String line : entry.getValue()) {
                        prn.println("    " + line);
                    }
                }
            }
            if (getFailOnError().getOrElse(true)) {
                throw new GradleException("Validation of generated asciidoctor files failed. See the report at " + clickableUrl(getReport().getAsFile().get()));
            }
        }
    }
}
