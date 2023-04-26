package io.micronaut.build

import com.diffplug.gradle.spotless.SpotlessTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.javadoc.Groovydoc

import static io.micronaut.build.BomSupport.coreBomArtifactId
import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault
/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautBuildCommonPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply(MicronautBasePlugin)
        project.pluginManager.apply(MicronautQualityChecksParticipantPlugin)
        def micronautBuild = project.extensions.findByType(MicronautBuildExtension)
        configureJavaPlugin(project, micronautBuild)
        configureDependencies(project, micronautBuild)
        configureTasks(project)
        configureIdeaPlugin(project)
        configureLicensePlugin(project)
        configureTestLoggerPlugin(project)
    }

    private void configureDependencies(Project project, MicronautBuildExtension micronautBuild) {
        def micronautVersionProvider = versionProviderOrDefault(project, 'micronaut', '')
        def groovyVersionProvider = versionProviderOrDefault(project, 'groovy', '')
        def groovyGroupProvider = groovyVersionProvider.map { groovyVersion ->
            groovyVersion.split("\\.").first().toInteger() <= 3 ?
                    'org.codehaus.groovy' :
                    'org.apache.groovy'
        }
        def byteBuddyVersionProvider = versionProviderOrDefault(project, 'bytebuddy', '1.12.18')
        def objenesisVersionProvider = versionProviderOrDefault(project, 'objenesis', '3.1')
        def logbackVersionProvider = versionProviderOrDefault(project, 'logback', '1.2.3')

        project.configurations {
            documentation
            globalBoms {
                canBeResolved = false
                canBeConsumed = false
            }
            implementation.extendsFrom(globalBoms)
            annotationProcessor.extendsFrom(globalBoms)
            testAnnotationProcessor.extendsFrom(globalBoms)
        }

        def injectGroovyIfProcessingEnabled = micronautBuild.enableProcessing.map { enabled ->
            enabled ? [project.dependencies.create("io.micronaut:micronaut-inject-groovy")] : []
        }
        project.dependencies.with { dependencies ->
            project.configurations.globalBoms.dependencies.addAllLater(micronautBuild.enableBom.zip(micronautVersionProvider) { enabled, micronautVersion ->
                if (enabled) {
                    if (micronautBuild.enforcedPlatform.get()) {
                        throw new GradleException("Do not use enforcedPlatform. Please remove the micronautBuild.enforcedPlatform setting")
                    }
                    String artifactId = coreBomArtifactId(micronautVersion)
                    [project.dependencies.platform("io.micronaut:$artifactId:$micronautVersion")]
                } else {
                    []
                }
            })
            project.configurations.annotationProcessor.dependencies.addAllLater(injectGroovyIfProcessingEnabled)
            project.configurations.testAnnotationProcessor.dependencies.addAllLater(injectGroovyIfProcessingEnabled)

            dependencies.addProvider("documentation", groovyGroupProvider.zip(groovyVersionProvider) { groovyGroup, groovyVersion ->
               "$groovyGroup:groovy-templates:$groovyVersion"
            })
            dependencies.addProvider("documentation", groovyGroupProvider.zip(groovyVersionProvider) { groovyGroup, groovyVersion ->
               "$groovyGroup:groovy-dateutil:$groovyVersion"
            })
            dependencies.addProvider("testCompileOnly", micronautVersionProvider.map { micronautVersion ->
                "io.micronaut:micronaut-inject-groovy:${micronautVersion}"
            })
            dependencies.addProvider("testImplementation",groovyGroupProvider.zip(groovyVersionProvider) { groovyGroup, groovyVersion ->
                "$groovyGroup:groovy-test:$groovyVersion"
            })
            dependencies.addProvider("testImplementation", byteBuddyVersionProvider.map {
                "net.bytebuddy:byte-buddy:$it"
            })
            dependencies.addProvider("testImplementation", objenesisVersionProvider.map {
                "org.objenesis:objenesis:$it"
            })
            dependencies.addProvider("testRuntimeOnly", logbackVersionProvider.map {
                "ch.qos.logback:logback-classic:$it"
            })
        }

        project.tasks.withType(Groovydoc).configureEach {
            classpath += project.configurations.documentation
        }
    }

    @SuppressWarnings('GrDeprecatedAPIUsage')
    private void configureJavaPlugin(Project project, MicronautBuildExtension micronautBuildExtension) {
        project.apply plugin: "groovy"
        project.apply plugin: "java-library"

        def javaPluginExtension = project.extensions.findByType(JavaPluginExtension)
        javaPluginExtension.toolchain.languageVersion.convention(micronautBuildExtension.javaVersion.map(JavaLanguageVersion::of))
        project.afterEvaluate {
            if (micronautBuildExtension.sourceCompatibility.isPresent() || micronautBuildExtension.targetCompatibility.isPresent()) {
                project.logger.warn """
The "sourceCompatibility" and "targetCompatibility" properties are deprecated.
Please use "micronautBuild.javaVersion" instead.
You can do this directly in the project, or, better, in a convention plugin if it exists.
"""
                // Remove convention or Gradle will complain that you can't use both
                javaPluginExtension.toolchain.languageVersion.convention(null)
                javaPluginExtension.with {
                    // orElse makes it work even if only one of the 2 properties is set
                    sourceCompatibility = micronautBuildExtension.sourceCompatibility.orElse(micronautBuildExtension.targetCompatibility).get()
                    targetCompatibility = micronautBuildExtension.targetCompatibility.orElse(micronautBuildExtension.sourceCompatibility).get()
                }
            }
        }

        def useVendorAsInput = project.providers.environmentVariable("MICRONAUT_TEST_USE_VENDOR")
                .map(Boolean::parseBoolean).getOrElse(false)
        project.tasks.withType(Test).configureEach {
            jvmArgs '-Duser.country=US'
            jvmArgs '-Duser.language=en'
            useJUnitPlatform()
            if (useVendorAsInput) {
                // This will have to be changed once we switch to toolchain support, since it will not be relevant anymore
                def vendor = project.providers.systemProperty("java.vendor").getOrElse("unknown")
                println("Configuring test task ${it.path} to execute specifically for vendor: $vendor")
                inputs.property("java.vendor", vendor)
            }
            retry {
                if (micronautBuildExtension.environment.isGithubAction().getOrElse(false)) {
                    maxRetries.set(2)
                    maxFailures.set(20)
                }
                failOnPassedAfterRetry.set(false)
            }
            predictiveSelection {
                enabled = micronautBuildExtension.environment.isTestSelectionEnabled()
            }
        }

        project.tasks.withType(GroovyCompile).configureEach {
            groovyOptions.forkOptions.jvmArgs.add('-Dgroovy.parameters=true')
        }

        project.afterEvaluate {
            def compileOptions = micronautBuildExtension.compileOptions
            project.tasks.withType(JavaCompile).configureEach {
                options.encoding = "UTF-8"
                options.compilerArgs.add('-parameters')
                if (micronautBuildExtension.enableProcessing.get()) {
                    options.compilerArgs.add("-Amicronaut.processing.group=$project.group")
                    options.compilerArgs.add("-Amicronaut.processing.module=micronaut-$project.name")
                }
                compileOptions.applyTo(options)
            }
            project.tasks.withType(GroovyCompile).configureEach {
                compileOptions.applyTo(options)
            }
        }

        project.tasks.withType(Jar).configureEach {
            manifest {
                attributes('Automatic-Module-Name': "${project.group}.${project.name}".replaceAll('[^\\w\\.\\$_]', "_"))
                attributes('Implementation-Version': project.findProperty("projectVersion"))
                attributes('Implementation-Title': project.findProperty("title"))
            }
        }

        ['compileClasspath', 'runtimeClasspath'].each { configName ->
            def config = project.configurations.getByName(configName)
            config.incoming.afterResolve { ResolvableDependencies deps ->
                def micronautVersion = versionProviderOrDefault(project, 'micronaut', '').get()
                def (major, minor, patch) = micronautVersion.tokenize('.')
                deps.resolutionResult.allComponents { ResolvedComponentResult result ->
                    def id = result.id
                    if (id instanceof ModuleComponentIdentifier) {
                        if (id.group == 'io.micronaut' && id.module == 'micronaut-core') {
                            def (resolvedMajor, resolvedMinor, resolvedPatch) = id.version.tokenize('.')
                            if (resolvedMajor != major || resolvedMinor != minor) {
                                throw new GradleException("Micronaut version mismatch: project declares $micronautVersion but resolved version is ${id.version}. You probably have a dependency which triggered an upgrade of micronaut-core. In order to determine where it comes from, you can run ./gradlew --dependencyInsight --configuration $configName --dependency io.micronaut:micronaut-core")
                            }
                        }
                    }
                }
            }
        }
    }

    void configureTasks(Project project) {
        project.tasks.register("allDeps", DependencyReportTask)
    }

    void configureIdeaPlugin(Project project) {
        project.with {
            apply plugin: 'idea'
            idea {
                module {
                    outputDir file('build/classes/java/main')
                    testOutputDir file('build/classes/groovy/test')
                    downloadJavadoc = providers.gradleProperty("idea.download.javadoc").map(Boolean::parseBoolean).getOrElse(false)
                    downloadSources = providers.gradleProperty("idea.download.sources").map(Boolean::parseBoolean).getOrElse(false)
                }
            }
        }
    }

    void configureLicensePlugin(Project project) {
        project.with {
            apply plugin: "com.diffplug.spotless"
            boolean hasGroovySources = file("src/main/groovy").exists()
            boolean hasKotlinSources = file("src/main/kotlin").exists()

            spotless {
                java {
                    licenseHeaderFile rootProject.file('config/spotless.license.java')
                    target 'src/main/java/**'
                }
                if (hasGroovySources) {
                    groovy {
                        licenseHeaderFile rootProject.file('config/spotless.license.java')
                        target 'src/main/groovy/**'
                    }
                }
                if (hasKotlinSources) {
                    kotlin {
                        licenseHeaderFile rootProject.file('config/spotless.license.java')
                        target 'src/main/kotlin/**'
                    }
                }
                format 'javaMisc', {
                    target 'src/main/**/package-info.java', 'src/main/**/module-info.java'
                    licenseHeaderFile rootProject.file('config/spotless.license.java'), '\\/\\*\\*'
                }
            }

            tasks.withType(SpotlessTask).configureEach {
                notCompatibleWithConfigurationCache("https://github.com/diffplug/spotless/issues/987")
            }
        }
    }

    void configureTestLoggerPlugin(Project project) {
        project.with {
            apply plugin: "com.adarshr.test-logger"

            testlogger {
                theme 'standard-parallel'
                showFullStackTraces true
                showStandardStreams true
                showPassedStandardStreams false
                showSkippedStandardStreams false
                showFailedStandardStreams true
            }
        }
    }

}
