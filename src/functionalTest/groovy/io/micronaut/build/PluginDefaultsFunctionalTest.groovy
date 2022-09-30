package io.micronaut.build

class PluginDefaultsFunctionalTest extends AbstractFunctionalTest {

    void "defaults to Java 17"() {
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
        run 'printJavaVersion'

        then:
        outputContains "Java version: 17"
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

}
