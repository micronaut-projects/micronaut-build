package io.micronaut.build

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.groovy.lang.groovydoc.tasks.GroovydocTask

import static io.micronaut.build.util.VersionHandling.versionOrDefault
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
        configureMiscPlugins(project)
    }

    private void configureDependencies(Project project, MicronautBuildExtension micronautBuild) {
        project.afterEvaluate {

            String micronautVersion = versionOrDefault(project, "micronaut")
            String groovyVersion = versionOrDefault(project, "groovy")

            String groovyGroup = groovyVersion.split("\\.").first().toInteger() <= 3 ?
                    'org.codehaus.groovy' :
                    'org.apache.groovy'

            project.configurations {
                documentation
                all {
                    resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                        String group = details.requested.group
                        if (group == groovyGroup) {
                            details.useVersion(groovyVersion)
                        }
                    }
                }
            }

            project.dependencies {
                if (micronautBuild.enableBom.get()) {
                    if (micronautBuild.enforcedPlatform.get()) {
                        throw new GradleException("Do not use enforcedPlatform. Please remove the micronautBuild.enforcedPlatform setting")
                    }
                    String p = "platform"
                    implementation "$p"("io.micronaut:micronaut-bom:${micronautVersion}")
                    annotationProcessor "$p"("io.micronaut:micronaut-bom:${micronautVersion}")
                    testAnnotationProcessor "$p"("io.micronaut:micronaut-bom:${micronautVersion}")
                    testImplementation "$p"("io.micronaut:micronaut-bom:${micronautVersion}")
                    compileOnly "$p"("io.micronaut:micronaut-bom:${micronautVersion}")
                }

                if (micronautBuild.enableProcessing.get()) {
                    annotationProcessor "io.micronaut:micronaut-inject-groovy:${micronautVersion}"
                    testAnnotationProcessor "io.micronaut:micronaut-inject-groovy:${micronautVersion}"
                }

                documentation "$groovyGroup:groovy-templates:$groovyVersion"
                documentation "$groovyGroup:groovy-dateutil:$groovyVersion"

                if (micronautVersion) {
                    testCompileOnly "io.micronaut:micronaut-inject-groovy:${micronautVersion}"
                }

                testImplementation "cglib:cglib-nodep:3.3.0"
                testImplementation "org.objenesis:objenesis:3.1"

                testRuntimeOnly "ch.qos.logback:logback-classic:1.2.3"
                testImplementation "$groovyGroup:groovy-test:$groovyVersion"
            }
        }


        project.tasks.withType(GroovydocTask).configureEach {
            classpath += project.configurations.documentation
        }
    }

    private void configureJavaPlugin(Project project, MicronautBuildExtension micronautBuildExtension) {
        project.apply plugin: "groovy"
        project.apply plugin: "java-library"
        project.pluginManager.apply('org.gradle.test-retry')

        project.afterEvaluate {
            JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
            convention.with {
                sourceCompatibility = micronautBuildExtension.sourceCompatibility.get()
                targetCompatibility = micronautBuildExtension.targetCompatibility.get()
            }
        }

        project.tasks.withType(Test).configureEach {
            jvmArgs '-Duser.country=US'
            jvmArgs '-Duser.language=en'

            String groovyVersion = project.findProperty("groovyVersion")
            if (groovyVersion?.startsWith("3")) {
                useJUnitPlatform()
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

    void configureMiscPlugins(Project project) {
        project.with {
            apply plugin: "io.spring.nohttp"
            nohttp {
                source.exclude "src/test/**", "build/**"
            }

        }
    }
}
