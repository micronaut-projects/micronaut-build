package io.micronaut.build.compat

import io.micronaut.build.AbstractFunctionalTest

class JavaBridgeAndSyntheticRuleFunctionalTest extends AbstractFunctionalTest {

    private static final String DUMMY1 = "subproject1/src/main/java/io/micronaut/subproject1/Dummy1.java"

    def "japiCmp fails when a public method is removed"() {
        given:
        withSample("test-micronaut-module")
        publishBaseline {
            it.replace("public boolean itWorks()", "public void removedLater() {\n  }\n\n  public boolean itWorks()")
        }

        when:
        fails ':subproject1:japiCmp'

        then:
        tasks {
            failed ':subproject1:japiCmp'
        }
        errorOutputContains 'Detected binary changes'
        reportText().contains 'Error Method io.micronaut.subproject1.Dummy1.removedLater(): Is not binary compatible'
    }

    def "japiCmp only warns when a bridge method is removed"() {
        given:
        withSample("test-micronaut-module")
        // the covariant override of the package-private Base.value() gets a bridge Object value()
        def base = file("subproject1/src/main/java/io/micronaut/subproject1/Base.java")
        base.text = baseClass("Object")
        file(DUMMY1).text = file(DUMMY1).text.replace("public class Dummy1 {", """public class Dummy1 extends Base {

  @Override
  public String value() {
    return "value";
  }
""")
        publishBaseline { it }
        // the bridge becomes CharSequence value(), so Dummy1 loses only its bridge Object value()
        base.text = baseClass("CharSequence")

        when:
        run ':subproject1:japiCmp'

        then:
        tasks {
            succeeded ':subproject1:japiCmp'
        }
        reportText().contains 'Warning Method io.micronaut.subproject1.Dummy1.value(): Is not binary compatible'
    }

    private String reportText() {
        file("subproject1/build/reports/binary-compatibility-subproject1.html").text
            .replaceAll(/<[^>]+>/, ' ')
            .replaceAll(/\s+/, ' ')
    }

    private static String baseClass(String returnType) {
        """package io.micronaut.subproject1;

abstract class Base {

  public $returnType value() {
    return null;
  }

}
"""
    }

    private void publishBaseline(Closure<String> baselineSource) {
        def dummy = file(DUMMY1)
        def original = dummy.text
        dummy.text = baselineSource(original)
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
