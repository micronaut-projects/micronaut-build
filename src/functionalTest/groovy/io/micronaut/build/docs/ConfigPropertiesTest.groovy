package io.micronaut.build.docs

import io.micronaut.build.AbstractFunctionalTest

import java.nio.file.Files

class ConfigPropertiesTest extends AbstractFunctionalTest {

    void "@Value in javadoc works as expected"() {
        given:
        withSample("test-micronaut-module")
        println testDirectory.toFile()

        and: 'we copy the grails-doc-files.jar to the prepareDocsResources directory'
        def path = Files.createDirectories(testDirectory.resolve("build/tmp/prepareDocsResources"))
        def stream = ConfigPropertiesTest.getResourceAsStream("/grails-doc-files.jar")
        def target = path.resolve("grails-doc-files.jar")
        Files.copy(stream, target)

        when: 'we build the docs'
        run 'docs'

        then:
        with(testDirectory.resolve("build/docs/guide/configurationreference.html").text) {
            contains("Generic config default value is 234.")
            contains("Normal config default value is 42.")
        }
    }
}
