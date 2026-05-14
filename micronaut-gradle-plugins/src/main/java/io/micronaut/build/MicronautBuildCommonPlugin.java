package io.micronaut.build;

import com.adarshr.gradle.testlogger.TestLoggerExtension;
import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessTask;
import io.micronaut.build.utils.DefaultVersions;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.build.BomSupport.coreBomArtifactId;
import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault;

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
public class MicronautBuildCommonPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        project.getPluginManager().apply(MicronautQualityChecksParticipantPlugin.class);
        MicronautBuildExtension micronautBuild = project.getExtensions().findByType(MicronautBuildExtension.class);
        configureJavaPlugin(project, micronautBuild);
        configureDependencies(project, micronautBuild);
        configureTasks(project);
        configureIdeaPlugin(project);
        configureLicensePlugin(project);
        configureTestLoggerPlugin(project);
    }

    private void configureDependencies(Project project, MicronautBuildExtension micronautBuild) {
        Provider<String> micronautVersionProvider = versionProviderOrDefault(project, "micronaut", "");
        // The Groovy version comes from core if not defined locally
        Provider<String> groovyVersionProvider = versionProviderOrDefault(project, "groovy", List.of("libs", "mn"), DefaultVersions.GROOVY_VERSION);
        Provider<String> groovyGroupProvider = groovyVersionProvider.map(groovyVersion -> {
            int major = Integer.parseInt(groovyVersion.split("\\.")[0]);
            return major <= 3 ? "org.codehaus.groovy" : "org.apache.groovy";
        });
        Provider<String> logbackVersionProvider = versionProviderOrDefault(project, "logback", List.of("libs", "mnLogging"), DefaultVersions.LOGBACK_VERSION);
        Provider<String> byteBuddyVersionProvider = versionProviderOrDefault(project, "bytebuddy", List.of("libs", "mnTest"), DefaultVersions.BYTEBUDDY_VERSION);
        Provider<String> objenesisVersionProvider = versionProviderOrDefault(project, "objenesis", List.of("libs", "mnTest"), DefaultVersions.OBJENESIS_VERSION);

        project.getConfigurations().create("documentation");
        Configuration globalBoms = project.getConfigurations().create("globalBoms", configuration -> {
            configuration.setCanBeResolved(false);
            configuration.setCanBeConsumed(false);
        });
        project.getConfigurations().getByName("implementation").extendsFrom(globalBoms);
        project.getConfigurations().getByName("annotationProcessor").extendsFrom(globalBoms);
        project.getConfigurations().getByName("testAnnotationProcessor").extendsFrom(globalBoms);

        Provider<List<Dependency>> injectGroovyIfProcessingEnabled = micronautBuild.getEnableProcessing().map(enabled -> {
            if (Boolean.TRUE.equals(enabled)) {
                return List.of(project.getDependencies().create("io.micronaut:micronaut-inject-groovy"));
            }
            return List.of();
        });
        DependencyHandler dependencies = project.getDependencies();
        globalBoms.getDependencies().addAllLater(micronautBuild.getEnableBom().zip(micronautVersionProvider, (enabled, micronautVersion) -> {
            if (Boolean.TRUE.equals(enabled)) {
                if (micronautBuild.getEnforcedPlatform().get()) {
                    throw new GradleException("Do not use enforcedPlatform. Please remove the micronautBuild.enforcedPlatform setting");
                }
                String artifactId = coreBomArtifactId(micronautVersion);
                return List.of(dependencies.platform("io.micronaut:" + artifactId + ":" + micronautVersion));
            }
            return List.of();
        }));
        project.getConfigurations().getByName("annotationProcessor").getDependencies().addAllLater(injectGroovyIfProcessingEnabled);
        project.getConfigurations().getByName("testAnnotationProcessor").getDependencies().addAllLater(injectGroovyIfProcessingEnabled);

        dependencies.addProvider("documentation", groovyGroupProvider.zip(groovyVersionProvider, (groovyGroup, groovyVersion) ->
            groovyGroup + ":groovy-templates:" + groovyVersion
        ));
        dependencies.addProvider("documentation", groovyGroupProvider.zip(groovyVersionProvider, (groovyGroup, groovyVersion) ->
            groovyGroup + ":groovy-dateutil:" + groovyVersion
        ));
        dependencies.addProvider("testCompileOnly", micronautVersionProvider.map(micronautVersion ->
            "io.micronaut:micronaut-inject-groovy:" + micronautVersion
        ));
        dependencies.addProvider("testImplementation", byteBuddyVersionProvider.map(version ->
            optionalDependency("net.bytebuddy:byte-buddy", version)
        ));
        dependencies.addProvider("testImplementation", objenesisVersionProvider.map(version ->
            optionalDependency("org.objenesis:objenesis", version)
        ));
        dependencies.addProvider("testRuntimeOnly", logbackVersionProvider.map(version ->
            optionalDependency("ch.qos.logback:logback-classic", version)
        ));
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher");

        project.getTasks().withType(Groovydoc.class).configureEach(groovydoc -> {
            groovydoc.setClasspath(groovydoc.getClasspath().plus(project.getConfigurations().getByName("documentation")));
        });
    }

    private static String optionalDependency(String groupArtifact, String version) {
        if (version == null || version.isEmpty()) {
            return null;
        }
        return groupArtifact + ":" + version;
    }

    private void configureJavaPlugin(Project project, MicronautBuildExtension micronautBuildExtension) {
        project.getPluginManager().apply(MicronautBuildJavaBasePlugin.class);
        project.getPluginManager().apply("groovy");
        project.getPluginManager().apply("java-library");

        project.getTasks().withType(GroovyCompile.class).configureEach(compile -> {
            compile.getGroovyOptions().getForkOptions().getJvmArgs().add("-Dgroovy.parameters=true");
        });

        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);

        project.afterEvaluate(unused -> {
            MicronautCompileOptions compileOptions = micronautBuildExtension.getCompileOptions();
            project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
                if (micronautBuildExtension.getEnableProcessing().get()) {
                    compile.getOptions().getCompilerArgs().add("-Amicronaut.processing.group=" + project.getGroup());
                    compile.getOptions().getCompilerArgs().add("-Amicronaut.processing.module=micronaut-" + project.getName());
                }
            });
            project.getTasks().withType(GroovyCompile.class).configureEach(compile -> compileOptions.applyTo(compile.getOptions()));
        });

        project.getTasks().withType(Jar.class).configureEach(jar -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("Automatic-Module-Name", (project.getGroup() + "." + project.getName()).replaceAll("[^\\w\\.\\$_]", "_"));
            attributes.put("Implementation-Version", project.findProperty("projectVersion"));
            attributes.put("Implementation-Title", project.findProperty("title"));
            jar.getManifest().attributes(attributes);
        });
        Object disableVersionCheck = project.getExtensions().findByName("disable.micronaut.version.check");
        if (disableVersionCheck == null || Boolean.FALSE.equals(disableVersionCheck)) {
            List.of("compileClasspath", "runtimeClasspath").forEach(configName -> {
                Configuration config = project.getConfigurations().getByName(configName);
                config.getIncoming().afterResolve(deps -> checkMicronautCoreVersion(project, configName, deps));
            });
        }
    }

    private static void checkMicronautCoreVersion(Project project, String configName, ResolvableDependencies deps) {
        String micronautVersion = versionProviderOrDefault(project, "micronaut", "").get();
        String[] declared = micronautVersion.split("\\.");
        String major = declared.length > 0 ? declared[0] : "";
        String minor = declared.length > 1 ? declared[1] : "";
        deps.getResolutionResult().allComponents(result -> {
            ComponentIdentifier id = result.getId();
            if (id instanceof ModuleComponentIdentifier module
                && "io.micronaut".equals(module.getGroup())
                && "micronaut-core".equals(module.getModule())) {
                String[] resolved = module.getVersion().split("\\.");
                String resolvedMajor = resolved.length > 0 ? resolved[0] : "";
                String resolvedMinor = resolved.length > 1 ? resolved[1] : "";
                if (!resolvedMajor.equals(major) || !resolvedMinor.equals(minor)) {
                    throw new GradleException("Micronaut version mismatch: project declares " + micronautVersion
                                              + " but resolved version is " + module.getVersion()
                                              + ". You probably have a dependency which triggered an upgrade of micronaut-core. "
                                              + "In order to determine where it comes from, you can run ./gradlew --dependencyInsight --configuration "
                                              + configName + " --dependency io.micronaut:micronaut-core");
                }
            }
        });
    }

    void configureTasks(Project project) {
        project.getTasks().register("allDeps", DependencyReportTask.class);
    }

    void configureIdeaPlugin(Project project) {
        project.getPluginManager().apply("idea");
        IdeaModel idea = project.getExtensions().getByType(IdeaModel.class);
        idea.getModule().setOutputDir(project.file("build/classes/java/main"));
        idea.getModule().setTestOutputDir(project.file("build/classes/groovy/test"));
        idea.getModule().setDownloadJavadoc(project.getProviders().gradleProperty("idea.download.javadoc").map(Boolean::parseBoolean).getOrElse(false));
        idea.getModule().setDownloadSources(project.getProviders().gradleProperty("idea.download.sources").map(Boolean::parseBoolean).getOrElse(false));
    }

    void configureLicensePlugin(Project project) {
        project.getPluginManager().apply("com.diffplug.spotless");
        boolean hasGroovySources = project.file("src/main/groovy").exists();
        boolean hasKotlinSources = project.file("src/main/kotlin").exists();

        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        spotless.java(java -> {
            java.licenseHeaderFile(project.getRootProject().file("config/spotless.license.java"));
            java.target("src/main/java/**");
        });
        if (hasGroovySources) {
            spotless.groovy(groovy -> {
                groovy.licenseHeaderFile(project.getRootProject().file("config/spotless.license.java"));
                groovy.target("src/main/groovy/**");
            });
        }
        if (hasKotlinSources) {
            spotless.kotlin(kotlin -> {
                kotlin.licenseHeaderFile(project.getRootProject().file("config/spotless.license.java"));
                kotlin.target("src/main/kotlin/**");
            });
        }
        spotless.format("javaMisc", format -> {
            format.target("src/main/**/package-info.java", "src/main/**/module-info.java");
            format.licenseHeaderFile(project.getRootProject().file("config/spotless.license.java"), "\\/\\*\\*");
        });

        project.getTasks().withType(SpotlessTask.class).configureEach(task -> {
            task.notCompatibleWithConfigurationCache("https://github.com/diffplug/spotless/issues/987");
        });
    }

    void configureTestLoggerPlugin(Project project) {
        project.getPluginManager().apply("com.adarshr.test-logger");

        TestLoggerExtension testLogger = project.getExtensions().getByType(TestLoggerExtension.class);
        testLogger.setTheme("standard-parallel");
        testLogger.setShowFullStackTraces(true);
        testLogger.setShowStandardStreams(true);
        testLogger.setShowPassedStandardStreams(false);
        testLogger.setShowSkippedStandardStreams(false);
        testLogger.setShowFailedStandardStreams(true);
    }
}
