/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.desugar.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logging;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The work of {@link DesugarLambdas}, which may run in a worker process.
 *
 * <p>Internal to the Micronaut build.</p>
 */
public abstract class DesugarLambdasAction implements WorkAction<DesugarLambdasAction.Parameters> {

    @Override
    public void execute() {
        Parameters parameters = getParameters();
        try {
            String summary = ModuleDesugaring.run(parameters.getClassesDirectory().get().getAsFile().toPath(),
                    paths(parameters.getClasspath()), paths(parameters.getCompileClasspath()),
                    parameters.getOutputDirectory().get().getAsFile().toPath(),
                    parameters.getReportFile().get().getAsFile().toPath());
            Logging.getLogger(DesugarLambdasAction.class).info(summary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> paths(ConfigurableFileCollection files) {
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file.toPath().toAbsolutePath().normalize());
        }
        return paths;
    }

    /**
     * The parameters of the work.
     */
    public interface Parameters extends WorkParameters {

        /**
         * The classes {@code compileJava} wrote.
         *
         * @return the directory
         */
        DirectoryProperty getClassesDirectory();

        /**
         * The other classes directories of the module, then its runtime class path.
         *
         * @return the class path
         */
        ConfigurableFileCollection getClasspath();

        /**
         * The compile class path.
         *
         * @return the class path
         */
        ConfigurableFileCollection getCompileClasspath();

        /**
         * Where the desugared copy goes.
         *
         * @return the directory
         */
        DirectoryProperty getOutputDirectory();

        /**
         * Where the report goes.
         *
         * @return the file
         */
        RegularFileProperty getReportFile();
    }
}
