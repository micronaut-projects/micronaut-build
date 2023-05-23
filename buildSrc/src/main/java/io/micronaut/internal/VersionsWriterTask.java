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
package io.micronaut.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;


public abstract class VersionsWriterTask extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getVersions();

    @Input
    public abstract Property<String> getClassName();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        var className = getClassName().get();
        var versions = getVersions().get();
        var outputDir = getOutputDirectory().get().getAsFile();
        var packageName = className.substring(0, className.lastIndexOf("."));
        var simpleClassName = className.substring(className.lastIndexOf(".") + 1);
        var parentDir = outputDir.toPath().resolve(packageName.replace('.', '/'));
        Files.createDirectories(parentDir);
        try (PrintWriter prn = new PrintWriter(new FileWriter(parentDir.resolve(simpleClassName + ".java").toFile()))) {
            prn.println("package " + packageName + ";");
            prn.println();
            prn.println("public class " + simpleClassName + " {");
            for (Map.Entry<String, String> entry : versions.entrySet()) {
                var key = entry.getKey();
                key = (key + "_VERSION").toUpperCase(Locale.US);
                prn.println("    public final static String " + key + " = \"" + entry.getValue() + "\";");
            }
            prn.println("}");
        }

    }
}
