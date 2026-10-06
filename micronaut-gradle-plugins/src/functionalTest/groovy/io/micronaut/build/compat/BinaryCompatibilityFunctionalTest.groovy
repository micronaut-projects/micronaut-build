package io.micronaut.build.compat

import io.micronaut.build.AbstractFunctionalTest

class BinaryCompatibilityFunctionalTest extends AbstractFunctionalTest {

    def "japiCmp honours changes accepted in #acceptedFile"() {
        given:
        withSample("test-micronaut-module")
        publishBaselineWithConstructorLaterRemoved()
        if (moduleSetting) {
            file("subproject1/build.gradle") << """
                micronautBuild {
                    binaryCompatibility {
                        acceptedRegressionsFile = file("config/accepted-api-changes.json")
                    }
                }
            """
        }

        when:
        fails ':subproject1:japiCmp', '--configuration-cache'

        then:
        tasks {
            failed ':subproject1:japiCmp'
        }
        errorOutputContains 'Detected binary changes'

        when:
        file(acceptedFile).text = """[
            {
                "type": "io.micronaut.subproject1.Dummy1",
                "member": "Constructor io.micronaut.subproject1.Dummy1(java.lang.String)",
                "reason": "Removed on purpose"
            }
        ]"""
        run ':subproject1:japiCmp', '--configuration-cache'

        then:
        tasks {
            succeeded ':subproject1:japiCmp'
        }

        where:
        acceptedFile                                | moduleSetting
        "config/accepted-api-changes.json"             | false
        "subproject1/config/accepted-api-changes.json" | true
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
        file("subproject1/build.gradle") << """
            micronautBuild {
                binaryCompatibility {
                    baselineVersion = "0.9.0"
                }
            }
        """
    }
}
