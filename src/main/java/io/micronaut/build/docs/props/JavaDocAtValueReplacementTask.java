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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
public abstract class JavaDocAtValueReplacementTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public abstract ConfigurableFileCollection getInputFiles();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void execute() throws IOException {
        File outputFile = getOutputFile().getAsFile().get();
        if (outputFile.getParentFile().isDirectory() || outputFile.getParentFile().mkdirs()) {
            for (File adocFile : getInputFiles().getFiles()) {
                if (adocFile.exists()) {
                    String configurationPropertiesClassName = null;
                    List<String> lines = Files.readAllLines(adocFile.toPath(), StandardCharsets.UTF_8);
                    try (PrintWriter prn = new PrintWriter(outputFile, StandardCharsets.UTF_8.name())) {
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if ("++++".equals(line) && lines.get(i + 1).startsWith("<a id=\"")) {
                                String subline = lines.get(i + 1).substring("<a id=\"".length());
                                configurationPropertiesClassName = subline.substring(0, subline.indexOf("\""));
                            }

                            String processedLine = line;

                            int attempts = 5;

                            while (processedLine.contains("{@value") && attempts > 0) {
                                AtValue atValueReplacement = JavaDocAtValueReplacement.atValueField(configurationPropertiesClassName, processedLine);

                                if (atValueReplacement != null && atValueReplacement.getType() != null && atValueReplacement.getFieldName() != null) {
                                    try {
                                        String resolvedValue = calculateResolvedValue(atValueReplacement.getType(), atValueReplacement.getFieldName());
                                        if (resolvedValue != null) {
                                            String result = processedLine.substring(0, processedLine.indexOf("{@value "));
                                            result += resolvedValue;
                                            String sub = processedLine.substring(processedLine.indexOf("{@value ") + "{@value ".length());
                                            sub = sub.substring(sub.indexOf('}') + "}".length());
                                            result += sub;
                                            processedLine = result;
                                        } else {
                                            getLogger().error("no resolved value for type: {} fieldname: {}", atValueReplacement.getType(), atValueReplacement.getFieldName());
                                        }
                                    } catch (StringIndexOutOfBoundsException e) {
                                        getLogger().error("StringIndexOutOfBoundsException - no resolved value for type: {} fieldname: {}", atValueReplacement.getType(), atValueReplacement.getFieldName());
                                    }
                                }
                                attempts--;
                            }
                            prn.print(processedLine + "\n");
                        }
                    }
                }
            }
        }
    }

    Set<File> calculateClassFiles(List<String> targetClassNames) {
        return getSourceFiles().getFiles()
                .stream()
                .filter(File::isFile)
                .filter(file -> file.getName().endsWith(".java"))
                .filter(file -> targetClassNames.stream().anyMatch(targetClassName -> file.getName().equals(targetClassName + ".java")))
                .collect(Collectors.toSet());
    }

    private static List<String> calculateTargetClassNames(String className) {
        return Arrays.stream(className.split("[.]"))
                .filter(s -> Character.isUpperCase(s.charAt(0)))
                .flatMap(s -> Arrays.stream(s.split("[$]")))
                .collect(Collectors.toList());
    }

    String calculateResolvedValue(String className, String fieldName) throws IOException {
        List<String> targetClassNames = calculateTargetClassNames(className);
        Set<File> classFiles = calculateClassFiles(targetClassNames);
        if (!classFiles.isEmpty()) {
            File f = classFiles.iterator().next();
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);

            Collections.reverse(targetClassNames);
            for (String targetClassName : targetClassNames) {
                String classEvaluated = null;
                String interfaceEvaluated = null;
                for (String line : lines) {
                    if (line.contains("class ")) {
                        String subLine = line.substring(line.indexOf("class ") + "class ".length());
                        if (subLine.indexOf(' ') != -1) {
                            classEvaluated = subLine.substring(0, subLine.indexOf(' '));
                        } else {
                            classEvaluated = subLine;
                        }
                    }
                    if (line.contains("interface ")) {
                        String subLine = line.substring(line.indexOf("interface ") + "interface ".length());
                        if (subLine.indexOf(' ') != -1) {
                            interfaceEvaluated = subLine.substring(0, subLine.indexOf(' '));
                        } else {
                            interfaceEvaluated = subLine;
                        }
                    }
                    if (classEvaluated != null && (classEvaluated.equals(targetClassName)) ||
                            (interfaceEvaluated != null && (interfaceEvaluated.equals(targetClassName)))
                    ) {
                        if (line.contains(" " + fieldName + " = ") && line.contains(";")) {
                            return line.substring(line.indexOf(fieldName + " = ") + (fieldName + " = ").length(), line.indexOf(';'));
                        }
                    }
                }
            }
        }
        return null;
    }
}
