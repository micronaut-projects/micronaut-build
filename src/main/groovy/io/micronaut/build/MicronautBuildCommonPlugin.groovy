package io.micronaut.build

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
        project.setVersion project.findProperty("projectVersion")
        MicronautBuildExtension micronautBuild = project.extensions.create('micronautBuild', MicronautBuildExtension)
        configureJavaPlugin(project, micronautBuild)
        configureDependencies(project)
        configureTasks(project)
        configureIdeaPlugin(project)
        configureLicensePlugin(project)
        configureTestLoggerPlugin(project)
        configureMiscPlugins(project)
        configureCheckstyle(project, micronautBuild)
    }

    private void configureDependencies(Project project) {
        String micronautVersion = project.findProperty("micronautVersion")
        String groovyVersion = project.findProperty("groovyVersion")

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
            annotationProcessor platform("io.micronaut:micronaut-bom:${micronautVersion}")
            implementation platform("io.micronaut:micronaut-bom:${micronautVersion}")
            testAnnotationProcessor platform("io.micronaut:micronaut-bom:${micronautVersion}")
            testImplementation platform("io.micronaut:micronaut-bom:${micronautVersion}")

            documentation "org.codehaus.groovy:groovy-templates:$groovyVersion"
            documentation "org.codehaus.groovy:groovy-dateutil:$groovyVersion"

            testImplementation "io.micronaut:micronaut-inject-groovy:${micronautVersion}"
            testImplementation "cglib:cglib-nodep:3.3.0"
            testImplementation "org.objenesis:objenesis:3.1"

            testRuntimeOnly "ch.qos.logback:logback-classic:1.2.3"
            testImplementation "org.codehaus.groovy:groovy-test:$groovyVersion"
        }

        project.tasks.withType(GroovydocTask) {
            classpath += project.configurations.documentation
        }
    }

    private void configureJavaPlugin(Project project, MicronautBuildExtension micronautBuildExtension) {
        project.apply plugin:"groovy"
        project.apply plugin:"java-library"

        project.afterEvaluate {
            JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
            convention.with {
                sourceCompatibility = micronautBuildExtension.sourceCompatibility
                targetCompatibility = micronautBuildExtension.targetCompatibility
            }
        }

        project.tasks.withType(Test) {
            jvmArgs '-Duser.country=US'
            jvmArgs '-Duser.language=en'

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

    void configureCheckstyle(Project project, MicronautBuildExtension micronautBuildExtension) {
        project.afterEvaluate {
            project.with {
                apply plugin: 'checkstyle'
                checkstyle {
                    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
                    toolVersion = micronautBuildExtension.checkstyleVersion

                    // Per submodule
                    maxErrors = 1
                    maxWarnings = 10

                    showViolations = true
                }
                checkstyleTest.enabled = false
                checkstyleMain.dependsOn('spotlessCheck')
            }
        }
    }

    void configureLicensePlugin(Project project) {
        project.with {
            apply plugin: "com.diffplug.gradle.spotless"
            spotless {
                java {
                    licenseHeaderFile rootProject.file('config/spotless.license.java')
                    targetExclude 'src/test/**', 'build/generated-src/**'
                }
                format 'javaMisc', {
                    target 'src/**/package-info.java', 'src/**/module-info.java'
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
        project.apply plugin: "io.spring.nohttp"
    }
}
