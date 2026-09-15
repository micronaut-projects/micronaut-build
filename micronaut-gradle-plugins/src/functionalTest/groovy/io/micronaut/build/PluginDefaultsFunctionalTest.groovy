package io.micronaut.build

import java.nio.file.Files
import java.nio.file.Path

class PluginDefaultsFunctionalTest extends AbstractFunctionalTest {

    void "defaults to Java 25"() {
        given:
        withSample("test-micronaut-module")

        file("subproject1/build.gradle") << """
            tasks.register("printJavaVersion") {
                doLast {
                    println "Java version: \${micronautBuild.javaVersion.get()}"
                }
            }
        """

        when:
        debug = true
        run 'printJavaVersion'

        then:
        outputContains "Java version: 25"
    }

    void "test java defaults to current JDK"() {
        given:
        withSample("test-micronaut-module")

        file("subproject1/build.gradle") << """
            tasks.register("printJavaVersion") {
                doLast {
                    println "Java version: \${micronautBuild.testJavaVersion.get()}"
                }
            }
        """

        when:
        debug = true
        run 'printJavaVersion'

        then:
        outputContains "Java version: ${System.getProperty('CURRENT_JDK')}"
    }

    void "writes micronaut build version for build logic included builds"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            plugins {
                id 'io.micronaut.build.shared.settings'
            }

            rootProject.name = 'test-build-logic'
            includeBuild 'build-logic'
        """
        buildFile << ""
        writeIncludedBuild("build-logic")

        when:
        run 'help'

        then:
        file("build-logic/gradle.properties").text.contains("micronaut-build-version=")
    }

    void "writes micronaut build version for configured included build directory"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            plugins {
                id 'io.micronaut.build.shared.settings'
            }

            micronautBuild {
                micronautBuildVersionDirectories.add('custom-build-logic')
            }

            rootProject.name = 'test-custom-build-logic'
            includeBuild 'custom-build-logic'
        """
        buildFile << ""
        writeIncludedBuild("custom-build-logic")
        file("other-build-logic/settings.gradle") << "rootProject.name = 'other-build-logic'"

        when:
        run 'help'

        then:
        file("custom-build-logic/gradle.properties").text.contains("micronaut-build-version=")
        !file("other-build-logic/gradle.properties").exists()
    }

    void "rejects unsafe micronaut build version directory"() {
        given:
        settingsFile << """
            plugins {
                id 'io.micronaut.build.shared.settings'
            }

            micronautBuild {
                micronautBuildVersionDirectories.add('../outside')
            }
        """
        buildFile << ""

        when:
        fails 'help'

        then:
        errorOutputContains "Micronaut build version directory must stay under the settings root: ../outside"
    }

    void "rejects configured micronaut build version directory symlink outside settings root"() {
        given:
        settingsFile << """
            plugins {
                id 'io.micronaut.build.shared.settings'
            }

            micronautBuild {
                micronautBuildVersionDirectories.add('custom-build-logic')
            }
        """
        buildFile << ""
        Path outsideDirectory = Files.createTempDirectory(testDirectory.parent, "outside-build-logic")
        Files.createSymbolicLink(testDirectory.resolve("custom-build-logic"), outsideDirectory)

        when:
        fails 'help'

        then:
        errorOutputContains "Micronaut build version directory must stay under the settings root: custom-build-logic"
        !outsideDirectory.resolve("gradle.properties").toFile().exists()
    }

    void "rejects default micronaut build version directory symlink outside settings root"() {
        given:
        settingsFile << """
            plugins {
                id 'io.micronaut.build.shared.settings'
            }
        """
        buildFile << ""
        Path outsideDirectory = Files.createTempDirectory(testDirectory.parent, "outside-build-logic")
        Files.createSymbolicLink(testDirectory.resolve("build-logic"), outsideDirectory)

        when:
        fails 'help'

        then:
        errorOutputContains "Micronaut build version directory must stay under the settings root: build-logic"
        !outsideDirectory.resolve("gradle.properties").toFile().exists()
    }

    void "can detect accidental upgrade of Micronaut"() {
        given:
        withSample("test-micronaut-module")

        file("subproject1/build.gradle") << """
            dependencies {
                implementation("io.micronaut:micronaut-core:4.8.4")
            }
        """

        when:
        fails 'compileJava'

        then:
        errorOutputContains "Micronaut version mismatch: project declares 4.6.3 but resolved version is 4.8.4. You probably have a dependency which triggered an upgrade of micronaut-core. In order to determine where it comes from, you can run ./gradlew --dependencyInsight --configuration compileClasspath --dependency io.micronaut:micronaut-core"
    }

    void "can use JUnit5 instead of Spock"() {
        given:
        withSample("test-micronaut-module")

        when:
        run 'testClasses'

        then:
        tasks {
            succeeded ':subproject1:compileTestGroovy' // uses Spock by default
            succeeded ':subproject2:compileTestJava' // overrides to JUnit5
        }
    }

    void "nullaway is opt-in for module projects"() {
        given:
        withSample("test-micronaut-module")

        file("subproject1/build.gradle") << """
            tasks.register("verifyNullAwayIsOptIn") {
                doLast {
                    assert project.extensions.findByName("micronautNullAway") == null
                    assert project.configurations.findByName("errorprone") == null
                    assert !project.pluginManager.hasPlugin("net.ltgt.errorprone")
                }
            }
        """

        when:
        run ':subproject1:verifyNullAwayIsOptIn'

        then:
        noExceptionThrown()
    }

    private void writeIncludedBuild(String name) {
        file("${name}/settings.gradle") << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            rootProject.name = '${name}'
        """
        file("${name}/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation(providers.gradleProperty("micronaut-build-version").map { "io.micronaut.build.internal:micronaut-kotlin-build-plugins:\${it}" }.get())
            }
        """
    }
}
