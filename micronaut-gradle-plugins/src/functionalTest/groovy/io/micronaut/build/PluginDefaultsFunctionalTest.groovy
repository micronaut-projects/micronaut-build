package io.micronaut.build

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
        file("build-logic/settings.gradle") << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            rootProject.name = 'build-logic'
        """
        file("build-logic/build.gradle") << """
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

        when:
        run 'help'

        then:
        file("build-logic/gradle.properties").text.contains("micronaut-build-version=")
    }

   void "warns if using #property compatibility"() {
        given:
        withSample("test-micronaut-module")

        file("subproject1/build.gradle") << """
            micronautBuild.$property = "8"
        """

        when:
        run 'help'

        then:
        outputContains """The "sourceCompatibility" and "targetCompatibility" properties are deprecated.
Please use "micronautBuild.javaVersion" instead.
You can do this directly in the project, or, better, in a convention plugin if it exists."""

       where:
       property << ["sourceCompatibility", "targetCompatibility"]
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
}
