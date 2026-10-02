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

import io.micronaut.build.MicronautBuildExtension;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Wires the lambda desugaring trial into a module: a {@code desugarLambdas} task over {@code compileJava}'s
 * output, whose copy the {@code jar} task packs instead of {@code compileJava}'s. Tests, javadoc, the sources jar
 * and the IDE keep the compiler's output, and the classes of other compilers go into the {@code jar} unchanged.
 *
 * <p>The trial is switched on with the Gradle property {@value #PROPERTY}: {@code true} for every module, or a
 * comma-separated list of project names, with or without their {@code micronaut-} prefix, for those modules only.
 * Without it the build is unchanged. Internal to the Micronaut build: see micronaut-build#956.</p>
 */
public final class LambdaDesugaring {

    /** The Gradle property that switches the trial on. */
    public static final String PROPERTY = "micronautBuild.desugarLambdas";

    /** The name of the task. */
    public static final String TASK_NAME = "desugarLambdas";

    private static final String PREFIX = "micronaut-";

    private LambdaDesugaring() {
    }

    /**
     * Wires the step into a module when the property selects it.
     *
     * @param project the module
     */
    public static void configure(Project project) {
        String value = project.getProviders().gradleProperty(PROPERTY).getOrNull();
        if (!selects(value, project.getName())) {
            return;
        }
        project.getPluginManager().withPlugin("java", plugin -> register(project));
    }

    /**
     * Whether the property selects a project.
     *
     * @param value the property's value, or {@code null}
     * @param name  the project's name
     * @return whether the step applies to the project
     */
    static boolean selects(String value, String name) {
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("false")) {
            return false;
        }
        if (value.trim().equalsIgnoreCase("true")) {
            return true;
        }
        String unprefixed = name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(selected -> selected.toLowerCase(Locale.ROOT))
                .anyMatch(selected -> selected.equals(name.toLowerCase(Locale.ROOT))
                        || selected.equals(unprefixed.toLowerCase(Locale.ROOT)));
    }

    private static void register(Project project) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        TaskProvider<JavaCompile> compileJava = project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class);
        Provider<Directory> compiled = compileJava.flatMap(AbstractCompile::getDestinationDirectory);
        Provider<JavaLauncher> launcher = toolchain(project, java);
        Provider<String> daemonJdk = project.getProviders().systemProperty("java.runtime.version");
        TaskProvider<DesugarLambdas> desugar = project.getTasks().register(TASK_NAME, DesugarLambdas.class, task -> {
            task.setDescription("Desugars the lambdas of the main Java classes for the jar (micronaut-build#956 trial)");
            task.getClassesDirectory().set(compiled);
            task.getClasspath().from(main.getOutput().getClassesDirs(),
                    project.getConfigurations().named(main.getRuntimeClasspathConfigurationName()));
            task.getCompileClasspath().from(main.getCompileClasspath());
            task.getJavaLauncher().convention(launcher);
            task.getJdkVersion().convention(launcher
                    .flatMap(toolchain -> DesugarLambdas.runsOn(toolchain)
                            ? project.getProviders().provider(() -> toolchain.getMetadata().getJavaRuntimeVersion())
                            : daemonJdk)
                    .orElse(daemonJdk));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("desugared-classes/java/main"));
            task.getReportFile().set(project.getLayout().getBuildDirectory().file("reports/desugarLambdas/main.txt"));
            task.getNativeImageName().convention(project.provider(() -> project.getGroup() + "/" + project.getName()));
        });
        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, jar -> {
            jar.from(desugar.flatMap(DesugarLambdas::getOutputDirectory));
            jar.exclude(new FromDirectory(compiled));
        });
    }

    private static Provider<JavaLauncher> toolchain(Project project, JavaPluginExtension java) {
        MicronautBuildExtension micronautBuild = project.getExtensions().findByType(MicronautBuildExtension.class);
        if (micronautBuild == null) {
            return project.getProviders().provider(() -> null);
        }
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        return micronautBuild.getUseToolchains().flatMap(useToolchains -> useToolchains
                ? toolchains.launcherFor(java.getToolchain())
                : project.getProviders().provider(() -> null));
    }

    /**
     * Matches the files of one directory, which the {@code jar} then takes from the desugared copy instead.
     */
    private static final class FromDirectory implements Spec<FileTreeElement> {

        private final Provider<Directory> directory;

        private FromDirectory(Provider<Directory> directory) {
            this.directory = directory;
        }

        @Override
        public boolean isSatisfiedBy(FileTreeElement element) {
            File root = directory.get().getAsFile();
            return element.getFile().toPath().toAbsolutePath().normalize().startsWith(root.toPath().toAbsolutePath().normalize());
        }
    }
}
