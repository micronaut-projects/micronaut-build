package io.micronaut.build

import java.nio.file.Files

class ReleasesDropdownFunctionalTest extends AbstractFunctionalTest {

    void "version dropdown is not populated from github by default"() {
        given:
        debug = true
        withSample("test-micronaut-module")

        and: 'we override the github slug to use micronaut-crac'
        testDirectory.resolve("gradle.properties").toFile() << "githubSlug=micronaut-projects/micronaut-crac"

        and: 'we copy the grails-doc-files.jar to the prepareDocsResources directory'
        def path = Files.createDirectories(testDirectory.resolve("build/tmp/prepareDocsResources"))
        def stream = ReleasesDropdownFunctionalTest.getResourceAsStream("/grails-doc-files.jar")
        def target = path.resolve("grails-doc-files.jar")
        Files.copy(stream, target)

        when: 'we build the docs'
        run 'docs'

        and: 'we find all the version options in the dropdown'
        def html = testDirectory.resolve("build/docs/guide/index.html").text
        def versions = html.findAll("<option.+?value='https://micronaut-projects.github.io/micronaut-crac/([^/]+)") { match, version ->
            version
        }

        then: 'no dropdown options'
        versions.empty
    }
}
