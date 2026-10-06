package io.micronaut.build.compat

import io.micronaut.build.AbstractFunctionalTest

class BinaryCompatibilityBaselineFunctionalTest extends AbstractFunctionalTest {

    def "japiCmp compares against the baseline found by findBaseline on the first run"() {
        given:
        withSample("test-micronaut-module")
        publishBaselineWithConstructorLaterRemoved()

        expect:
        !file("subproject1/build/baseline.txt").exists()

        when:
        fails ':subproject1:japiCmp'

        then:
        tasks {
            succeeded ':subproject1:findBaseline'
            failed ':subproject1:japiCmp'
        }
        file("subproject1/build/baseline.txt").text == "0.9.0"
        errorOutputContains 'Detected binary changes'
        file("subproject1/build/reports/binary-compatibility-subproject1.html").text.contains("against 0.9.0")
    }

    def "japiCmp fails instead of comparing against nothing when the configuration cache resolves the baseline before findBaseline"() {
        given:
        withSample("test-micronaut-module")
        publishBaselineWithConstructorLaterRemoved()

        when:
        fails ':subproject1:japiCmp', '--configuration-cache'

        then:
        tasks {
            succeeded ':subproject1:findBaseline'
            failed ':subproject1:japiCmp'
        }
        errorOutputContains 'No baseline was resolved for io.micronaut.project-template:micronaut-subproject1'

        when:
        fails ':subproject1:japiCmp', '--configuration-cache'

        then:
        tasks {
            failed ':subproject1:japiCmp'
        }
        errorOutputContains 'Detected binary changes'
        file("subproject1/build/reports/binary-compatibility-subproject1.html").text.contains("against 0.9.0")
    }

    private void publishBaselineWithConstructorLaterRemoved() {
        def dummy = file("subproject1/src/main/java/io/micronaut/subproject1/Dummy1.java")
        def original = dummy.text
        dummy.text = original.replace("public boolean itWorks()", "public Dummy1() {\n  }\n\n  public Dummy1(String removedLater) {\n  }\n\n  public boolean itWorks()")
        gradlePropertiesFile.text = gradlePropertiesFile.text.replace("projectVersion=1.0.0-SNAPSHOT", "projectVersion=0.9.0")
        run ':subproject1:publishAllPublicationsToBuildRepository'

        dummy.text = original
        gradlePropertiesFile.text = gradlePropertiesFile.text.replace("projectVersion=0.9.0", "projectVersion=0.9.1-SNAPSHOT")
        // the baseline resolver copies the project repositories when the plugin is applied
        settingsFile << """
            def baselineRepo = new File(settingsDir, "build/repo")
            gradle.beforeProject { p ->
                p.repositories.maven { url = baselineRepo }
            }
        """
        // findBaseline looks the previous release up in the published maven-metadata.xml
        file("subproject1/build.gradle") << """
            tasks.named("findBaseline") {
                baseRepository = rootProject.layout.buildDirectory.dir("repo").get().asFile.toURI().toString()
            }
        """
        // a fresh checkout: nothing left behind by the publishing build
        assert file("subproject1/build").deleteDir()
    }
}
