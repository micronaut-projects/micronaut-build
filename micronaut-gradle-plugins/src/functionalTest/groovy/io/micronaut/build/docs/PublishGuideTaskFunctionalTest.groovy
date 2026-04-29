package io.micronaut.build.docs

import io.micronaut.build.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files

class PublishGuideTaskFunctionalTest extends AbstractFunctionalTest {

    void "snippet source changes invalidate publishGuide"() {
        given:
        withSample("test-micronaut-module")
        copyDocResourcesJar()
        file("src/main/docs/guide/introduction.adoc").text = "snippet::example.Hello[tags=body]\n"
        def snippet = file("test-suite/src/test/java/example/Hello.java")
        snippet.text = snippetSource("one")

        when:
        run "publishGuide"

        then:
        result.task(":publishGuide").outcome == TaskOutcome.SUCCESS
        guideHtml.contains('return "one";')

        when:
        snippet.text = snippetSource("two")
        run "publishGuide"

        then:
        result.task(":publishGuide").outcome == TaskOutcome.SUCCESS
        guideHtml.contains('return "two";')
        !guideHtml.contains('return "one";')
    }

    private String getGuideHtml() {
        testDirectory.resolve("build/working/02-docs-raw/all/guide/introduction.html").text
    }

    private void copyDocResourcesJar() {
        def path = Files.createDirectories(testDirectory.resolve("build/tmp/prepareDocsResources"))
        def stream = PublishGuideTaskFunctionalTest.getResourceAsStream("/grails-doc-files.jar")
        def target = path.resolve("grails-doc-files.jar")
        Files.copy(stream, target)
    }

    private static String snippetSource(String value) {
        """
package example;

class Hello {
    // tag::body[]
    String value() {
        return "${value}";
    }
    // end::body[]
}
""".stripIndent()
    }
}
