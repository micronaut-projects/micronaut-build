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
package io.micronaut.build.graalvm;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Generates Micronaut entries for GraalVM reachability metadata's library-and-framework list.
 */
@CacheableTask
public abstract class GenerateGraalVmReachabilityMetadataTask extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getModuleDescriptions();

    @Input
    public abstract Property<String> getMinimumVersion();

    @Input
    public abstract ListProperty<String> getMetadataLocations();

    @Input
    public abstract ListProperty<String> getTestsLocations();

    @Input
    public abstract Property<String> getTestLevel();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public GenerateGraalVmReachabilityMetadataTask() {
        setDescription("Generates Micronaut entries for GraalVM reachability metadata's library-and-framework list.");
        setGroup("release");
    }

    @TaskAction
    public void generate() throws IOException {
        var outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, renderEntries(), StandardCharsets.UTF_8);
    }

    private String renderEntries() {
        var entries = new ArrayList<>(getModuleDescriptions().get().entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));

        var json = new StringBuilder();
        json.append("[\n");
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            json.append("  {\n");
            json.append("    \"artifact\": \"").append(escapeJson(entry.getKey())).append("\",\n");
            json.append("    \"description\": \"").append(escapeJson(entry.getValue())).append("\",\n");
            json.append("    \"details\": [\n");
            json.append("      {\n");
            json.append("        \"minimum_version\": \"").append(escapeJson(getMinimumVersion().get())).append("\",\n");
            json.append("        \"metadata_locations\": ");
            appendStringArray(json, getMetadataLocations().get());
            json.append(",\n");
            json.append("        \"tests_locations\": ");
            appendStringArray(json, getTestsLocations().get());
            json.append(",\n");
            json.append("        \"test_level\": \"").append(escapeJson(getTestLevel().get())).append("\"\n");
            json.append("      }\n");
            json.append("    ]\n");
            json.append("  }");
            if (i + 1 < entries.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("]\n");
        return json.toString();
    }

    private static void appendStringArray(StringBuilder json, List<String> values) {
        json.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append("\"").append(escapeJson(values.get(i))).append("\"");
        }
        json.append("]");
    }

    private static String escapeJson(String value) {
        var escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
