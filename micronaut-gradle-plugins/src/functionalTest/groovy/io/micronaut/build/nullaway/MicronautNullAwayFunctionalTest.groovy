package io.micronaut.build.nullaway

import io.micronaut.build.AbstractFunctionalTest

class MicronautNullAwayFunctionalTest extends AbstractFunctionalTest {

    private static final String BUILD_SCRIPT = '''
        import io.micronaut.build.utils.DefaultVersions
        import net.ltgt.gradle.errorprone.CheckSeverity
        import net.ltgt.gradle.errorprone.ErrorProneOptionsKt

        plugins {
            id("io.micronaut.build.internal.base-module")
        }

        micronautBuild {
            nullAway = true
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
            nullAway = true
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

    void "nullaway opt-in configures default checks and dependencies"() {
        given:
        settingsFile.text = "rootProject.name = 'nullaway-default'"
        gradlePropertiesFile.text = "projectVersion=1.0.0\ntitle=Demo"
        buildFile.text = BUILD_SCRIPT

        when:
        run "verifyNullAway"

        then:
        noExceptionThrown()
    }

    void "nullaway plugin disables NullAway for tck projects"() {
        given:
        settingsFile.text = "rootProject.name = 'demo-tck'"
        gradlePropertiesFile.text = "projectVersion=1.0.0\ntitle=Demo"
        buildFile.text = TCK_BUILD_SCRIPT

        when:
        run "verifyTckNullAway"

        then:
        noExceptionThrown()
    }
}
