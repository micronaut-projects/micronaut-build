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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

/**
 * Writes a copy of the classes {@code compileJava} produced in which every lambda and method-reference call site
 * that can be desugared calls a class generated for it, and a report of the sites rewritten and kept. Only the
 * {@code jar} task reads the copy.
 *
 * <p>The step runs in the Gradle daemon, or, when the module compiles with a Java toolchain of version 25 or
 * later, in a worker process on that toolchain, so that it judges the JDK's classes against the JDK the module
 * compiles with.</p>
 *
 * <p>Internal to the Micronaut build: a trial, see micronaut-build#956.</p>
 */
@CacheableTask
public abstract class DesugarLambdas extends DefaultTask {

    /** The lowest JDK that runs the step: the ClassFile API is final from JDK 24, and the plugins target 25. */
    static final int MINIMUM_JDK = 25;

    /**
     * The classes {@code compileJava} wrote.
     *
     * @return the directory
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getClassesDirectory();

    /**
     * What the module runs with: its other classes directories, then its runtime class path. Every type a
     * generated class names must resolve against it, the module's classes or the JDK.
     *
     * @return the class path
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The module's compile class path, which only tells which kept sites name a compile-only type.
     *
     * @return the class path
     */
    @CompileClasspath
    public abstract ConfigurableFileCollection getCompileClasspath();

    /**
     * The version of the JDK that runs the step, which the output depends on.
     *
     * @return the {@code java.runtime.version}
     */
    @Input
    public abstract Property<String> getJdkVersion();

    /**
     * The toolchain the module compiles with, when it uses one.
     *
     * @return the launcher
     */
    @Internal
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * The desugared copy of the classes.
     *
     * @return the directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * The report of the sites rewritten and kept.
     *
     * @return the file
     */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    void desugar() {
        JavaLauncher launcher = getJavaLauncher().getOrNull();
        WorkQueue queue;
        if (runsOn(launcher)) {
            queue = getWorkerExecutor().processIsolation(spec ->
                    spec.getForkOptions().setExecutable(launcher.getExecutablePath().getAsFile()));
        } else {
            queue = getWorkerExecutor().noIsolation();
        }
        queue.submit(DesugarLambdasAction.class, parameters -> {
            parameters.getClassesDirectory().set(getClassesDirectory());
            parameters.getClasspath().from(getClasspath());
            parameters.getCompileClasspath().from(getCompileClasspath());
            parameters.getOutputDirectory().set(getOutputDirectory());
            parameters.getReportFile().set(getReportFile());
        });
    }

    /**
     * Whether the step runs on a toolchain rather than in the daemon.
     *
     * @param launcher the module's toolchain, or {@code null}
     * @return whether there is a toolchain that can run the step
     */
    static boolean runsOn(JavaLauncher launcher) {
        return launcher != null && launcher.getMetadata().getLanguageVersion().asInt() >= MINIMUM_JDK;
    }
}
