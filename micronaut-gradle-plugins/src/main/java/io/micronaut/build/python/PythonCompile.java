/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.build.python;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compiles Python sources with the Pyronaut compiler.
 * <p>
 * The compiler is given each source directory relative to the project directory, so that the
 * {@code @PythonApplication(src = ...)} value it compiles into the output carries no absolute path.
 * The project directory itself reaches the annotation processor as a compiler option, because a
 * worker daemon does not run in the project directory. That keeps the outputs relocatable and
 * therefore cacheable.
 * <p>
 * The compiler walks whole directories, so include and exclude patterns on the sources (for example on the
 * {@code python} source directory set) are applied by the task: a source root whose Python files are not all
 * included is replaced by a copy of its included files under the task's temporary directory.
 */
@CacheableTask
public abstract class PythonCompile extends DefaultTask {

    /**
     * The Python sources to compile, usually the filtered file tree of the {@code python} source directory set.
     * A directory added to it is compiled as a whole.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSource();

    /**
     * The root directories of the {@link #getSource() sources}, which define their package names. Their contents
     * are tracked through the sources.
     */
    @Internal
    public abstract ConfigurableFileCollection getSourceRoots();

    @Input
    @Optional
    public abstract ListProperty<String> getJvmArgs();

    /**
     * The worker heap; Gradle's worker default of 512m is too small for the GraalPy processor.
     */
    @Input
    @Optional
    public abstract Property<String> getMaxHeapSize();

    @Input
    @Optional
    public abstract MapProperty<String, String> getSystemProperties();

    @Input
    @Optional
    public abstract MapProperty<String, String> getEnvironmentVariables();

    @Internal
    @Option(option = "debug-python-compiler", description = "Debug the Pyronaut compiler")
    public abstract Property<Boolean> getDebugCompiler();

    /**
     * The classpath of the Pyronaut compiler itself.
     */
    @Classpath
    public abstract ConfigurableFileCollection getCompilerClasspath();

    /**
     * The compile classpath of the Python sources.
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    protected abstract ProjectLayout getLayout();

    private Map<String, String> getMergedSystemProperties() {
        var systemProperties = new LinkedHashMap<>(getSystemProperties().getOrElse(Map.of()));
        systemProperties.putAll(Map.of(
            "org.graalvm.python.vfs.allow_multiple", "true",
            "org.graalvm.python.vfs.multiple_vfs_checks_as_warning", "true"
        ));
        return Collections.unmodifiableMap(systemProperties);
    }

    private List<String> getMergedJvmArgs() {
        var jvmArgs = new ArrayList<>(getJvmArgs().getOrElse(List.of()));
        if (getDebugCompiler().getOrElse(false)) {
            jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005");
        }
        return jvmArgs;
    }

    @TaskAction
    void compile() throws IOException {
        var outputDir = getDestinationDir().getAsFile().get().toPath();
        var projectDir = getLayout().getProjectDirectory().getAsFile().toPath().toAbsolutePath().normalize();
        getFileSystemOperations().delete(spec -> spec.delete(outputDir));
        Files.createDirectories(outputDir);
        // A process-isolated worker is reused for matching fork options within one build, so several
        // Python compile tasks share one JVM start; the compiler itself is rebuilt per submission.
        var queue = getWorkerExecutor().processIsolation(spec -> {
            spec.getClasspath().from(getCompilerClasspath(), getClasspath());
            spec.forkOptions(fork -> {
                fork.setMaxHeapSize(getMaxHeapSize().getOrElse("2g"));
                fork.systemProperties(getMergedSystemProperties());
                fork.environment(getEnvironmentVariables().getOrElse(Map.of()));
                fork.jvmArgs(getMergedJvmArgs());
            });
        });
        var destDir = getDestinationDir().getAsFile().get().getAbsolutePath();
        var sourceDirs = new ArrayList<String>();
        for (var sourceDir : compilerSourceDirs()) {
            sourceDirs.add(relocatableSourcePath(projectDir, sourceDir));
        }
        if (sourceDirs.isEmpty()) {
            return;
        }
        // one work item: the roots share the destination, so they must not compile concurrently
        queue.submit(PythonCompileWorkAction.class, parameters -> {
            parameters.getSourceDirs().set(sourceDirs);
            parameters.getSourceRoot().set(projectDir.toString());
            parameters.getDestinationDir().set(destDir);
            parameters.getClasspath().from(getCompilerClasspath(), getClasspath());
        });
        queue.await();
    }

    /**
     * Returns the directories to hand to the compiler. The compiler takes directories and compiles every Python file
     * below them, so a root is passed as is only when all of its Python files are included; otherwise its included
     * files are copied to a staging directory which is passed instead, and a root without included files is dropped.
     */
    private List<Path> compilerSourceDirs() throws IOException {
        var roots = new LinkedHashSet<Path>();
        for (var root : getSourceRoots().getFiles()) {
            roots.add(root.toPath().toAbsolutePath().normalize());
        }
        for (var file : getSource().getFiles()) {
            // a directory added to the sources directly is a root of its own
            if (file.isDirectory()) {
                roots.add(file.toPath().toAbsolutePath().normalize());
            }
        }
        roots.removeIf(root -> !Files.isDirectory(root));
        var includedByRoot = new LinkedHashMap<Path, Set<Path>>();
        roots.forEach(root -> includedByRoot.put(root, new TreeSet<>()));
        for (var file : getSource().getAsFileTree().getFiles()) {
            var path = file.toPath().toAbsolutePath().normalize();
            if (isPythonSource(path)) {
                ownerRoot(roots, path).ifPresent(root -> includedByRoot.get(root).add(path));
            }
        }
        var stagingDir = getTemporaryDir().toPath().resolve("python-sources");
        getFileSystemOperations().delete(spec -> spec.delete(stagingDir));
        var sourceDirs = new ArrayList<Path>();
        int index = 0;
        for (var entry : includedByRoot.entrySet()) {
            var root = entry.getKey();
            var included = entry.getValue();
            if (included.isEmpty()) {
                continue;
            }
            if (included.equals(compiledPythonSources(root))) {
                sourceDirs.add(root);
            } else {
                var staged = stagingDir.resolve(String.valueOf(index++));
                for (var source : included) {
                    var target = staged.resolve(root.relativize(source).toString());
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
                sourceDirs.add(staged);
            }
        }
        return sourceDirs;
    }

    /**
     * Returns the innermost root containing the file.
     */
    private static java.util.Optional<Path> ownerRoot(Set<Path> roots, Path file) {
        return roots.stream()
            .filter(file::startsWith)
            .max(Comparator.comparingInt(Path::getNameCount));
    }

    /**
     * Returns the Python files the compiler would find below a root: it skips hidden directories.
     */
    private static Set<Path> compiledPythonSources(Path root) throws IOException {
        var sources = new TreeSet<Path>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && (Files.isHidden(dir) || dir.getFileName().toString().startsWith("."))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isPythonSource(file)) {
                    sources.add(file.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return sources;
    }

    private static boolean isPythonSource(Path file) {
        return file.getFileName().toString().endsWith(".py");
    }

    /**
     * Returns the source directory relative to the project directory when it is located inside it,
     * otherwise its absolute path. The annotation processor resolves relative paths against the project directory.
     */
    private static String relocatableSourcePath(Path projectDir, Path sourceDir) {
        var absoluteSource = sourceDir.toAbsolutePath().normalize();
        if (absoluteSource.startsWith(projectDir)) {
            return projectDir.relativize(absoluteSource).toString();
        }
        return absoluteSource.toString();
    }
}
