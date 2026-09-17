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

import io.micronaut.build.MicronautBuildExtension;
import io.micronaut.build.MicronautBuildExtensionPlugin;
import io.micronaut.build.utils.VersionHandling;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import java.util.List;

/**
 * Adds Python (Pyronaut) compilation support to a project: every source set gets a
 * {@code src/<sourceSet>/python} source directory whose sources are compiled by the
 * Pyronaut compiler into the source set output.
 * <p>
 * The compiler is resolved from the {@code pyronautCompiler} configuration. Unless
 * {@code micronautBuild.python.compilerVersion} is empty, the plugin adds
 * {@code io.micronaut:micronaut-inject-python} and {@code io.micronaut:micronaut-context-python}
 * of that version to it.
 * <p>
 * The {@code Test} tasks of the project only run when the {@code python-ci} Gradle property is
 * set (see {@link MicronautPythonExtension#getTestsEnabled()}), so that the regular CI compiles
 * the Python sources while the Python tests run in the dedicated "Python CI" workflow on GraalVM.
 * That workflow runs the root {@code pythonCheck} task, which aggregates the {@code check} tasks of
 * every project applying this plugin; a build can register {@code pythonCheck} in its root project
 * itself to add other projects to it.
 */
public class MicronautPythonPlugin implements Plugin<Project> {
    public static final String PYRONAUT_COMPILER_CONFIGURATION = "pyronautCompiler";
    public static final String PYRONAUT_COMPILER_CLASSPATH_CONFIGURATION = "pyronautCompilerClasspath";
    public static final String PYTHON_EXTENSION_NAME = "python";
    public static final String PYTHON_SOURCE_DIRECTORY_SET_NAME = "python";
    public static final String COMPILE_PYTHON_TASK_NAME = "compilePython";
    public static final String PYTHON_CI_PROPERTY = "python-ci";
    /**
     * Root project task which runs the {@code check} task of every project applying this plugin,
     * the entry point of the "Python CI" workflow.
     */
    public static final String PYTHON_CHECK_TASK_NAME = "pythonCheck";

    private static final String MICRONAUT_GROUP = "io.micronaut";
    private static final List<String> COMPILER_ARTIFACTS = List.of("micronaut-inject-python", "micronaut-context-python");

    @Override
    public void apply(Project project) {
        // Pyronaut generates Java classes so we need the Java plugin
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(MicronautBuildExtensionPlugin.class);
        var extension = createExtension(project);
        var pyronautCompiler = createPyronautCompilerConfiguration(project, extension);
        var pyronautCompilerClasspath = createPyronautCompilerClasspath(project, pyronautCompiler);
        configureSourceSets(project, extension, pyronautCompilerClasspath);
        configureTestTasks(project, extension);
        registerPythonCheckTask(project);
    }

    /**
     * Hooks the {@code check} task of this project into the root {@code pythonCheck} task,
     * registering the latter if the root project didn't.
     *
     * @param project the project
     */
    private static void registerPythonCheckTask(Project project) {
        var rootTasks = project.getRootProject().getTasks();
        TaskProvider<Task> pythonCheck;
        if (rootTasks.getNames().contains(PYTHON_CHECK_TASK_NAME)) {
            pythonCheck = rootTasks.named(PYTHON_CHECK_TASK_NAME);
        } else {
            pythonCheck = rootTasks.register(PYTHON_CHECK_TASK_NAME, task -> {
                task.setGroup("verification");
                task.setDescription("Runs the checks of every project with Python sources.");
            });
        }
        var check = project.getTasks().named("check");
        pythonCheck.configure(task -> task.dependsOn(check));
    }

    private static MicronautPythonExtension createExtension(Project project) {
        var micronautBuild = project.getExtensions().getByType(MicronautBuildExtension.class);
        var extension = ((ExtensionAware) micronautBuild).getExtensions().create(PYTHON_EXTENSION_NAME, MicronautPythonExtension.class);
        extension.getCompilerVersion().convention(VersionHandling.versionProviderOrDefault(project, "micronaut", ""));
        extension.getTestsEnabled().convention(
            project.getProviders().gradleProperty(PYTHON_CI_PROPERTY).map(unused -> true).orElse(false)
        );
        return extension;
    }

    /**
     * Skips the test tasks unless Python tests are enabled.
     *
     * @param project the project
     * @param extension the python extension
     */
    private static void configureTestTasks(Project project, MicronautPythonExtension extension) {
        project.getTasks().withType(Test.class).configureEach(test ->
            test.onlyIf("Python tests only run with -P" + PYTHON_CI_PROPERTY + " (micronautBuild.python.testsEnabled)",
                unused -> extension.getTestsEnabled().get())
        );
    }

    /**
     * Creates the configuration used to declare the dependencies of the Pyronaut compiler.
     *
     * @param project the project
     * @param extension the python extension
     * @return the compiler configuration
     */
    private static Configuration createPyronautCompilerConfiguration(Project project, MicronautPythonExtension extension) {
        return project.getConfigurations().create(PYRONAUT_COMPILER_CONFIGURATION, conf -> {
            conf.setDescription("Dependencies of the Pyronaut compiler");
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(false);
            conf.getDependencies().addAllLater(extension.getCompilerVersion().orElse("").map(version -> {
                if (version.isBlank()) {
                    return List.<Dependency>of();
                }
                return COMPILER_ARTIFACTS.stream()
                    .map(artifact -> project.getDependencies().create(MICRONAUT_GROUP + ":" + artifact + ":" + version))
                    .toList();
            }));
        });
    }

    /**
     * Creates a resolvable configuration which resolves the Pyronaut compiler.
     *
     * @param project the project
     * @param pyronautCompiler the configuration which declares the compiler dependencies
     * @return the resolvable configuration
     */
    private static Configuration createPyronautCompilerClasspath(Project project,
                                                                 Configuration pyronautCompiler) {
        return project.getConfigurations().create(PYRONAUT_COMPILER_CLASSPATH_CONFIGURATION, conf -> {
            conf.setDescription("Classpath of the Pyronaut compiler");
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.extendsFrom(pyronautCompiler);
            conf.attributes(attrs -> {
                var objects = project.getObjects();
                attrs.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.class, LibraryElements.JAR));
                attrs.attribute(Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category.class, Category.LIBRARY));
                attrs.attribute(Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });
    }

    /**
     * Generates a Python source directory set and creates a compilation task for
     * each source set.
     *
     * @param project the project
     * @param extension the python extension
     * @param pyronautCompilerClasspath the compiler classpath configuration
     */
    private static void configureSourceSets(Project project,
                                            MicronautPythonExtension extension,
                                            Configuration pyronautCompilerClasspath) {
        project.getPluginManager().withPlugin("java-base", unused -> {
            var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.all(sourceSet -> createPythonSourceDirectory(project, extension, pyronautCompilerClasspath, sourceSet));
        });
    }

    private static void createPythonSourceDirectory(Project project,
                                                    MicronautPythonExtension extension,
                                                    Configuration pyronautCompilerClasspath,
                                                    SourceSet sourceSet) {
        var sourceDirectorySet =
            project.getObjects().sourceDirectorySet(PYTHON_SOURCE_DIRECTORY_SET_NAME, "Python sources");
        sourceSet.getExtensions().add(PYTHON_SOURCE_DIRECTORY_SET_NAME, sourceDirectorySet);
        var sourceSetName = sourceSet.getName();
        sourceDirectorySet.srcDir("src/" + sourceSetName + "/python");
        var pythonCompileClasspath = createPythonCompileClasspath(project, sourceSet);
        var compileTask =
            createCompileTask(project, extension, pyronautCompilerClasspath, pythonCompileClasspath, sourceSet, compileTaskName(sourceSetName), sourceDirectorySet);
        var classesDirs = sourceSet.getOutput().getClassesDirs();
        if (classesDirs instanceof ConfigurableFileCollection cfc) {
            // Declare that the Python compiler task contributes new classes
            cfc.from(compileTask.flatMap(PythonCompile::getDestinationDir));
        } else {
            throw new IllegalStateException(
                "Unexpected classes directory type: " + classesDirs.getClass());
        }
    }

    /**
     * Creates the classpath the Python sources of a source set are compiled against. It declares the
     * same dependencies as the source set's compile classpath but requests jars: Gradle resolves
     * sibling projects of a compile classpath to their bare classes directories, without resources,
     * which would hide the {@code META-INF/services} files of annotation processors and type element
     * visitors from the Pyronaut compiler.
     *
     * @param project the project
     * @param sourceSet the source set
     * @return the resolvable classpath configuration
     */
    private static Configuration createPythonCompileClasspath(Project project, SourceSet sourceSet) {
        var compileClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
        return project.getConfigurations().create(pythonCompileClasspathName(sourceSet.getName()), conf -> {
            conf.setDescription("Compile classpath of the " + sourceSet.getName() + " Python sources.");
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.setVisible(false);
            conf.extendsFrom(compileClasspath);
            conf.attributes(attrs -> {
                var objects = project.getObjects();
                attrs.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API));
                attrs.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attrs.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
                attrs.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
            });
        });
    }

    /**
     * Returns the name of the Python compile classpath configuration of a source set, for example
     * {@code pythonCompileClasspath} for {@code main} and {@code testPythonCompileClasspath} for {@code test}.
     *
     * @param sourceSetName the source set name
     * @return the configuration name
     */
    public static String pythonCompileClasspathName(String sourceSetName) {
        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSetName)) {
            return "pythonCompileClasspath";
        }
        return sourceSetName + "PythonCompileClasspath";
    }

    /**
     * Returns the name of the Python compile task of a source set, for example
     * {@code compilePython} for {@code main} and {@code compileTestPython} for {@code test}.
     *
     * @param sourceSetName the source set name
     * @return the task name
     */
    public static String compileTaskName(String sourceSetName) {
        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSetName)) {
            return COMPILE_PYTHON_TASK_NAME;
        }
        return "compile" + Character.toUpperCase(sourceSetName.charAt(0)) + sourceSetName.substring(1) + "Python";
    }

    /**
     * Creates a new Pyronaut compilation task.
     *
     * @param project the project
     * @param extension the python extension providing the default compiler arguments
     * @param pyronautCompilerClasspath the compiler classpath
     * @param pythonCompileClasspath the classpath the Python sources are compiled against
     * @param sourceSet the source set for which to generate a compilation task
     * @param taskName the name of the task to create
     * @param sourceDirectorySet the Python source directory set
     */
    private static TaskProvider<PythonCompile> createCompileTask(Project project,
                                                                 MicronautPythonExtension extension,
                                                                 Configuration pyronautCompilerClasspath,
                                                                 Configuration pythonCompileClasspath,
                                                                 SourceSet sourceSet,
                                                                 String taskName,
                                                                 SourceDirectorySet sourceDirectorySet) {
        return project.getTasks().register(taskName, PythonCompile.class, task -> {
            task.setGroup("build");
            task.setDescription("Compiles the " + sourceSet.getName() + " Python sources with the Pyronaut compiler.");
            task.getSource().convention(sourceDirectorySet.getSourceDirectories());
            task.getDestinationDir()
                .convention(project.getLayout().getBuildDirectory().dir("classes/python/" + sourceSet.getName()));
            task.getCompilerClasspath().from(pyronautCompilerClasspath);
            task.getClasspath().from(pythonCompileClasspath);
            // not a convention: a task calling compilerArgs.add(...) would discard it, so the task
            // property starts with the extension arguments and a task appends to them
            task.getCompilerArgs().addAll(extension.getCompilerArgs());
        });
    }
}
