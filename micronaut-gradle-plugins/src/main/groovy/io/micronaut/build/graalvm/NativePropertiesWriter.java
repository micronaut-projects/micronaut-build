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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@CacheableTask
public abstract class NativePropertiesWriter extends DefaultTask {

    private static final String NEWLINE = " \\\n       ";

    /**
     * The content of the native-image.properties file to generate.
     * This cannot be used if any of the other convenience properties are used.
     * @return the contents of the native image properties file
     */
    @Input
    @Optional
    abstract Property<String> getContents();

    /**
     * The list of classes/packages to initialize at runtime
     * @return the list of classes/packages to initialize at runtime
     */
    @Input
    @Optional
    abstract ListProperty<String> getInitializeAtRuntime();

     /**
     * The list of classes/packages to initialize at build time
     * @return the list of classes/packages to initialize at build time
     */
    @Input
    @Optional
    abstract ListProperty<String> getInitializeAtBuildtime();

    /**
     * The list of features to enable
     * @return the list of features
     */
    @Input
    @Optional
    abstract ListProperty<String> getFeatures();

    /**
     * Extra arguments to pass to the native image args. For example,
     * if you put the key "-H:APIFunctionPrefix" and a value with a single
     * entry "truffle_isolate_", then we would generate the following argument:
     * -H:APIFunctionPrefix=truffle_isolate_
     * @return the extra arguments
     */
    @Input
    @Optional
    abstract MapProperty<String, List<String>> getExtraArguments();

    @Input
    abstract Property<String> getGroupId();

    @Input
    abstract Property<String> getArtifactId();

    /**
     * The output directory where the native-image.properties file is generated.
     * @return the output directory
     */
    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generateFile() throws IOException {
        List<String> initializeAtRuntime = getInitializeAtRuntime().getOrElse(Collections.emptyList());
        List<String> initializeAtBuildtime = getInitializeAtBuildtime().getOrElse(Collections.emptyList());
        List<String> features = getFeatures().getOrElse(Collections.emptyList());
        Map<String, List<String>> extraArguments = getExtraArguments().getOrElse(Collections.emptyMap());
        Path outputPath = getOutputDirectory().get().getAsFile().toPath();
        String nativePropertiesPath = "META-INF/native-image/" + getGroupId().get() + "/" + getArtifactId().get() + "/native-image.properties";
        Files.createDirectories(outputPath.resolve(nativePropertiesPath).getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath.resolve(nativePropertiesPath))) {
            boolean hasContents = getContents().isPresent();
            if (hasContents) {
                writer.write(getContents().get());
                writer.write("\n");
                if (!initializeAtBuildtime.isEmpty()) {
                    throw new InvalidUserCodeException("You cannot use both explicit contents and initializeAtBuildtime properties to generate the native-image.properties file");
                }
                if (!initializeAtRuntime.isEmpty()) {
                    throw new InvalidUserCodeException("You cannot use both explicit contents and initializeAtRuntime properties to generate the native-image.properties file");
                }
                if (!features.isEmpty()) {
                    throw new InvalidUserCodeException("You cannot use both explicit contents and features properties to generate the native-image.properties file");
                }
                if (!extraArguments.isEmpty()) {
                    throw new InvalidUserCodeException("You cannot use both explicit contents and extraArguments properties to generate the native-image.properties file");
                }
            } else {
                StringBuilder args = new StringBuilder();
                if (!initializeAtBuildtime.isEmpty()) {
                    args.append("--initialize-at-build-time=");
                    args.append(String.join(",", initializeAtBuildtime));
                }
                if (!initializeAtRuntime.isEmpty()) {
                    if (args.length() > 0) {
                        args.append(NEWLINE);
                    }
                    args.append("--initialize-at-run-time=");
                    args.append(String.join(",", initializeAtRuntime));
                }
                if (!features.isEmpty()) {
                    if (args.length() > 0) {
                        args.append(NEWLINE);
                    }
                    args.append("--features=");
                    args.append(String.join(",", features));
                }
                if (!extraArguments.isEmpty()) {
                    for (Map.Entry<String, List<String>> entry : extraArguments.entrySet()) {
                        if (args.length() > 0) {
                            args.append(NEWLINE);
                        }
                        args.append(entry.getKey()).append("=");
                        args.append(String.join(",", entry.getValue()));
                    }
                }
                writer.write("Args = ");
                writer.write(args.toString());
                writer.write("\n");
            }
        }
    }

}
