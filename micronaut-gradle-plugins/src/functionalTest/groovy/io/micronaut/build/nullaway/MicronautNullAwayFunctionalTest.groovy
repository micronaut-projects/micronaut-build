package io.micronaut.build.nullaway

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class MicronautNullAwayFunctionalTest extends Specification {

    private static final String BUILD_SCRIPT = '''
        import io.micronaut.build.utils.DefaultVersions
        import net.ltgt.gradle.errorprone.CheckSeverity
        import net.ltgt.gradle.errorprone.ErrorProneOptionsKt

        plugins {
            id("io.micronaut.build.internal.base-module")
        }

        micronautBuild {
            enableProcessing.set(false)
        }

        tasks.register("verifyNullAway") {
            doLast {
                def compileJava = tasks.named("compileJava").get()
                def options = ErrorProneOptionsKt.getErrorprone(compileJava.options)
                assert options.checks.get()["NullAway"] == CheckSeverity.ERROR
                assert options.checks.get()["MissingOverride"] == CheckSeverity.ERROR
                assert options.checkOptions.get()["NullAway:AnnotatedPackages"] == "io.micronaut"

                def compileTestJava = tasks.named("compileTestJava").get()
                def testOptions = ErrorProneOptionsKt.getErrorprone(compileTestJava.options)
                assert testOptions.checks.get()["NullAway"] == CheckSeverity.OFF
                assert testOptions.checks.get()["MissingOverride"] == CheckSeverity.ERROR

                def deps = configurations.errorprone.dependencies.collect { "${it.group}:${it.name}:${it.version}" } as Set
                assert deps.contains("com.uber.nullaway:nullaway:${DefaultVersions.NULLAWAY_VERSION}")
                assert deps.contains("com.google.errorprone:error_prone_core:${DefaultVersions.ERROR_PRONE_CORE_VERSION}")
            }
        }
    '''

    private static final String TCK_BUILD_SCRIPT = '''
        import net.ltgt.gradle.errorprone.CheckSeverity
        import net.ltgt.gradle.errorprone.ErrorProneOptionsKt

        plugins {
            id("io.micronaut.build.internal.base-module")
        }

        micronautBuild {
            enableProcessing.set(false)
        }

        tasks.register("verifyTckNullAway") {
            doLast {
                def compileJava = tasks.named("compileJava").get()
                def options = ErrorProneOptionsKt.getErrorprone(compileJava.options)
                assert options.checks.get()["NullAway"] == CheckSeverity.OFF
                assert options.checks.get()["MissingOverride"] == CheckSeverity.ERROR
            }
        }
    '''

    void "nullaway plugin configures default checks and dependencies"() {
        given:
        Path projectDir = Files.createTempDirectory("nullaway-default")
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'nullaway-default'")
        Files.writeString(projectDir.resolve("gradle.properties"), "projectVersion=1.0.0\ntitle=Demo")
        Files.writeString(projectDir.resolve("build.gradle"), BUILD_SCRIPT)

        expect:
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("verifyNullAway")
                .build()

        cleanup:
        projectDir?.toFile()?.deleteDir()
    }

    void "nullaway plugin disables NullAway for tck projects"() {
        given:
        Path projectDir = Files.createTempDirectory("nullaway-tck")
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'demo-tck'")
        Files.writeString(projectDir.resolve("gradle.properties"), "projectVersion=1.0.0\ntitle=Demo")
        Files.writeString(projectDir.resolve("build.gradle"), TCK_BUILD_SCRIPT)

        expect:
        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("verifyTckNullAway")
                .build()

        cleanup:
        projectDir?.toFile()?.deleteDir()
    }
}
