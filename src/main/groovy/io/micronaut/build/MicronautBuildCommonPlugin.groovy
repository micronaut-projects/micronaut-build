package io.micronaut.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.groovy.lang.groovydoc.tasks.GroovydocTask

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautBuildCommonPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.repositories.jcenter()
        project.version project.findProperty("projectVersion")
        configureJavaPlugin(project)
        configureDependencies(project)
        configureTasks(project)
        configureIdeaPlugin(project)
        configureCheckstyle(project)
        configureDependencyUpdates(project)
        configureLicensePlugin(project)
        configureTestLoggerPlugin(project)
    }

    private void configureDependencies(Project project) {
        String micronautVersion = project.findProperty("micronautVersion")
        String groovyVersion = project.findProperty("groovyVersion")
        String spockVersion = project.findProperty("spockVersion")
        String micronautTestVersion = project.findProperty("micronautTestVersion")

        project.configurations {
            documentation
            all {
                resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                    String group = details.requested.group
                    if(group == 'org.codehaus.groovy') {
                        details.useVersion(groovyVersion)
                    }
                }
            }
        }

        project.dependencies {
            annotationProcessor enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}")
            implementation enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}")
            testAnnotationProcessor enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}")
            testImplementation enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}")

            documentation "org.codehaus.groovy:groovy-templates:$groovyVersion"
            documentation "org.codehaus.groovy:groovy-dateutil:$groovyVersion"

            testImplementation("org.spockframework:spock-core:${spockVersion}") {
                exclude module:'groovy-all'
            }

            testImplementation "io.micronaut:micronaut-inject-groovy:${micronautVersion}"
            testImplementation "io.micronaut.test:micronaut-test-spock:$micronautTestVersion"
            testImplementation "cglib:cglib-nodep:3.3.0"
            testImplementation "org.objenesis:objenesis:3.1"

            testRuntimeOnly "ch.qos.logback:logback-classic:1.2.3"
            testImplementation "org.codehaus.groovy:groovy-test:$groovyVersion"
        }

        project.tasks.withType(GroovydocTask) {
            classpath += project.configurations.documentation
        }
    }

    private void configureJavaPlugin(Project project) {
        project.apply plugin:"groovy"
        project.apply plugin:"java-library"

        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        convention.with {
            sourceCompatibility = '1.8'
            targetCompatibility = '1.8'
        }

        project.tasks.withType(Test) {
            jvmArgs '-Duser.country=US'
            jvmArgs '-Duser.language=en'
            testLogging {
                exceptionFormat = 'full'
            }
            afterSuite {
                System.out.print(".")
                System.out.flush()
            }

            reports.html.enabled = !System.getenv("GITHUB_ACTIONS")
            reports.junitXml.enabled = !System.getenv("GITHUB_ACTIONS")

        }

        project.tasks.withType(GroovyCompile) {
            groovyOptions.forkOptions.jvmArgs.add('-Dgroovy.parameters=true')
        }

        project.tasks.withType(JavaCompile){
            options.encoding = "UTF-8"
            options.compilerArgs.add('-parameters')
        }

        project.tasks.withType(Jar) {
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

    void configureCheckstyle(Project project) {
        project.with {
            apply plugin: 'checkstyle'
            checkstyle {
                configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
                toolVersion = 8.32

                // Per submodule
                maxErrors = 1
                maxWarnings = 10

                showViolations = true
            }
            checkstyleTest.enabled = false
        }
    }

    void configureDependencyUpdates(Project project) {
        project.with {
            apply plugin: "com.github.ben-manes.versions"
            dependencyUpdates {
                checkForGradleUpdate = true
                checkConstraints = true
                rejectVersionIf { mod ->
                    mod.candidate.version ==~ /.+(-|\.?)(b|M|RC)\d.*/ ||
                            ["alpha", "beta", "milestone", "preview"].any { mod.candidate.version.toLowerCase(Locale.ENGLISH).contains(it) } ||
                            mod.candidate.group == 'io.micronaut' // managed by the micronaut version
                }
                outputFormatter = { result ->
                    if (!result.outdated.dependencies.isEmpty()) {
                        def upgradeVersions = result.outdated.dependencies
                        if (!upgradeVersions.isEmpty()) {
                            println "\nThe following dependencies have later ${revision} versions:"
                            upgradeVersions.each { dep ->
                                def currentVersion = dep.version
                                println " - ${dep.group}:${dep.name} [${currentVersion} -> ${dep.available[revision]}]"
                                if (dep.projectUrl != null) {
                                    println "     ${dep.projectUrl}"
                                }
                            }
                            throw new GradleException('Abort, there are dependencies to update.')
                        }
                    }
                }
            }

            project.tasks.getByName("check").dependsOn('dependencyUpdates')
        }
    }

    void configureLicensePlugin(Project project) {
        project.with {
            apply plugin: "com.diffplug.gradle.spotless"
            spotless {
                java {
                    licenseHeaderFile rootProject.file('config/spotless.license.java')
                    target '**/*.java'
                    targetExclude 'src/test/**', 'build/generated-src/**'
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
}
