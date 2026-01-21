package io.micronaut.build.graalpy;

import org.graalvm.python.pyinterfacegen.TypeCheckPyiTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates a GraalPy-oriented Python module (.pyi stubs + runtime __init__.py)
 * from project Java sources using the j2pyi Javadoc doclet, and packages the
 * results into the published JAR under META-INF/graalpy-module.
 * <p>
 * This avoids introducing a new external Gradle plugin and instead reuses the
 * existing per-project Javadoc flow with a custom doclet.
 */
public class GraalPyModulePlugin implements Plugin<Project> {
    // Default coordinate for the doclet; can be overridden via project property 'graalPyDocletCoordinate'
    private static final String DEFAULT_DOCLET_COORD = "org.graalvm.python:j2pyi-doclet:25.1.0-SNAPSHOT";
    // Doclet class
    private static final String DOCLET_CLASS = "org.graalvm.python.pyinterfacegen.J2PyiDoclet";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        // Configuration that provides the j2pyi doclet artifact.
        Configuration docletConf = project.getConfigurations().maybeCreate("graalPyDoclet");
        docletConf.setCanBeConsumed(false);
        docletConf.setCanBeResolved(true);
        docletConf.defaultDependencies(deps -> {
            String coord = (String) project.findProperty("graalPyDocletCoordinate");
            if (coord == null || coord.isBlank()) {
                coord = DEFAULT_DOCLET_COORD;
            }
            deps.add(project.getDependencies().create(coord));
        });

        // FIXME: Take out maven local once pyinterfacegen is published to Maven Central.
        project.getRepositories().mavenLocal();
        project.getRepositories().mavenCentral();

        // Register a dedicated Javadoc run using the custom doclet.
        var generatePyi = project.getTasks().register(
                "generateGraalPyModule",
                Javadoc.class,
                javadoc -> configureGraalPyTask(project, javadoc, docletConf)
        );

        var typeCheckGraalPyModule = project.getTasks().register(
                "typeCheckGraalPyModule",
                TypeCheckPyiTask.class,
                (TypeCheckPyiTask typeCheckTask) -> {
                    typeCheckTask.setGroup("Documentation");
                    typeCheckTask.setDescription("Type check the generated Python module for conversion errors.");
                    typeCheckTask.getModuleDir().set(getDestinationDir(project));
                    typeCheckTask.dependsOn(generatePyi);
                }
        );

        // Package the generated module into the main jar
        project.getTasks().withType(Jar.class).configureEach(jar -> {
            jar.dependsOn(generatePyi);
            jar.from(generatePyi.map(Javadoc::getDestinationDir), spec ->
                spec.into("META-INF/graalpy-module")
            );
        });
    }

    private static void configureGraalPyTask(Project project, Javadoc javadoc, Configuration docletConf) {
        javadoc.setGroup("Documentation");
        javadoc.setDescription("Generate a Python module (.pyi + runtime) for GraalPy interop using the j2pyi doclet");
        // Destination is a unified module root produced by the doclet
        javadoc.setDestinationDir(getDestinationDir(project).get().getAsFile());

        // Use main source set by default.
        //
        // IMPORTANT: in mixed-language modules (e.g. Kotlin), Javadoc needs the compiled classes
        // of those other languages on its classpath, otherwise it will fail to resolve types.
        // Micronaut's build adds such outputs to the regular `javadoc` task; mirror that behavior
        // here so the generated GraalPy module isn't empty in Kotlin-heavy modules.
        project.getPlugins().withType(JavaPlugin.class, unused -> {
            JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
            SourceSet main = javaExt.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            // If we put compiled classes and processed resources on the Javadoc classpath, we must ensure
            // those outputs are built first (Gradle 9+ validates implicit dependencies).
            javadoc.dependsOn(project.getTasks().named(main.getClassesTaskName()));
            javadoc.dependsOn(project.getTasks().named(main.getProcessResourcesTaskName()));

            if (javadoc.getSource().isEmpty()) {
                javadoc.setSource(main.getAllJava().matching(p -> p.include("**/*.java")));
            }

            if (javadoc.getClasspath().isEmpty()) {
                Set<File> cp = new LinkedHashSet<>();
                cp.addAll(main.getCompileClasspath().getFiles());
                cp.addAll(main.getOutput().getClassesDirs().getFiles());
                File resourcesDir = main.getOutput().getResourcesDir();
                if (resourcesDir != null) {
                    cp.add(resourcesDir);
                }
                javadoc.setClasspath(project.files(cp));
            }
        });

        StandardJavadocDocletOptions opts = (StandardJavadocDocletOptions) javadoc.getOptions();
        opts.setDoclet(DOCLET_CLASS);

        // Resolve doclet jars lazily and attach both to execution and cache fingerprint
        // StandardJavadocDocletOptions#setDocletpath expects a List<File>, so resolve the configuration
        opts.setDocletpath(new java.util.ArrayList<>(docletConf.resolve()));

        // Pass module metadata: module name is the Gradle project name, version uses the Gradle project version
        opts.addStringOption("Xj2pyi-moduleName", project.getName());
        String version = String.valueOf(project.getVersion());
        if (version != null && !"unspecified".equalsIgnoreCase(version)) {
            opts.addStringOption("Xj2pyi-moduleVersion", version);
        }

        // Keep Javadoc quiet; unresolved bits are handled by the doclet
        opts.addBooleanOption("quiet", true);
        // Ensure JDK 21 javadoc doesn't receive unsupported -notimestamp
        opts.setNoTimestamp(false);

        // Optional package mapping: configure with -PgraalPyPackageMap=com.example=example,com.foo=foo
        Object pkgMap = project.findProperty("graalPyPackageMap");
        if (pkgMap instanceof String s && !s.isBlank()) {
            opts.addStringOption("Xj2pyi-packageMap", s);
        }

        // Only treat Micronaut types as typed. Everything else is emitted as dynamic/untyped.
        // Map Java package 'io.micronaut.*' to 'micronaut.*' in Python to avoid conflict with stdlib 'io'.
        opts.addStringOption("Xj2pyi-packageMap", "io.micronaut=micronaut");
        opts.addStringOption("Xj2pyi-assumedTypedPackageGlobs", "io.micronaut.**");
    }

    private static @NonNull Provider<Directory> getDestinationDir(Project project) {
        return project.getLayout().getBuildDirectory().dir("graalpy-module/" + project.getName());
    }
}
