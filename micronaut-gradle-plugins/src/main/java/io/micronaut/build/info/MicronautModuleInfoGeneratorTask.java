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
package io.micronaut.build.info;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;

import static org.apache.commons.text.StringEscapeUtils.escapeJava;

@CacheableTask
public abstract class MicronautModuleInfoGeneratorTask extends DefaultTask {
    @Input
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getClassName();

    @Input
    public abstract Property<String> getModuleName();

    @Input
    public abstract Property<String> getModuleVersion();

    @Input
    public abstract Property<String> getModuleDescription();

    @Input
    public abstract Property<String> getGroupId();

    @Input
    public abstract Property<String> getArtifactId();

    @Input
    @Optional
    public abstract Property<String> getParentModuleId();

    @Input
    public abstract SetProperty<String> getTags();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    public abstract FileSystemOperations getFsOperations();

    public MicronautModuleInfoGeneratorTask() {
        setDescription("Generates a Micronaut Module Info descriptor");
    }

    @TaskAction
    public void generateDescriptor() throws IOException {
        getFsOperations().delete(spec -> spec.delete(getOutputDirectory()));
        var packageName = getPackageName().get();
        var packageDir = packageName.replace('.', '/');
        var outputDir = getOutputDirectory().get().getAsFile().toPath().resolve(packageDir);
        Files.createDirectories(outputDir);
        var className = getClassName().get();
        var fileName = className + ".java";

        var groupId = getGroupId().get();
        var artifactId = getArtifactId().get();
        var ga = groupId + ":" + artifactId;
        var name = escapeJava(getModuleName().get());
        var description = escapeJava(removeExtraNewLines(getModuleDescription().get()));
        var version = getModuleVersion().get();
        var mavenCoords = "new MavenCoordinates(\"" + groupId + "\", \"" + artifactId + "\", \"" + version + "\")";
        var parent = getParentModuleId().map(id -> {
            if (id.equals(ga)) {
                // Parent is the same module. To be safe, set it to null to avoid cyclic dependency.
                return "null";
            } else {
                return "\"" + id + "\"";
            }
        }).getOrElse("null");
        var tags = "Set.of(" + getTags().get().stream().map(tag -> "\"" + escapeJava(tag) + "\"").collect(Collectors.joining(",")) + ")";

        try (var writer = new PrintWriter(Files.newBufferedWriter(outputDir.resolve(fileName), StandardCharsets.UTF_8))) {
            writer.println("package " + packageName + ";");
            writer.println();
            writer.println("import io.micronaut.module.info.AbstractMicronautModuleInfo;");
            writer.println("import io.micronaut.module.info.MavenCoordinates;");
            writer.println("import java.util.Set;");
            writer.println();
            writer.println("public class " + className + " extends AbstractMicronautModuleInfo {");
            writer.println("    public " + className + "() {");
            writer.println("        super(\"" + ga + "\", ");
            writer.println("              \"" + name + "\",");
            writer.println("              \"" + description + "\",");
            writer.println("              \"" + version + "\",");
            writer.println("              " + mavenCoords + ",");
            writer.println("              " + parent + ",");
            writer.println("              " + tags);
            writer.println("        );");
            writer.println("    }");
            writer.println("}");
        }

        var metaInfDir = outputDir.resolve("META-INF/services");
        Files.createDirectories(metaInfDir);
        try (var writer = new PrintWriter(Files.newBufferedWriter(metaInfDir.resolve("io.micronaut.module.info.MicronautModuleInfo"), StandardCharsets.UTF_8))) {
            writer.println(packageName + "." + className);
        }
    }

    private static String removeExtraNewLines(String input) {
        return input.replaceAll("^[\\r\\n]+|[\\r\\n]+$", "");
    }
}
