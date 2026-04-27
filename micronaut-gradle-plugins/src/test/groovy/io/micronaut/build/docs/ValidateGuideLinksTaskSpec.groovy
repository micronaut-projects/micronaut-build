package io.micronaut.build.docs

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class ValidateGuideLinksTaskSpec extends Specification {

    @TempDir
    Path testDirectory

    void "passes when local file and fragment links resolve"() {
        given:
        def docsDir = file("docs")
        file("docs/guide/index.html").text = '''
            <a href="configurationreference.html#config-reference">Configuration Reference</a>
            <a href="https://docs.micronaut.io/latest/api/">API</a>
            <a href="//cdn.example.com/library.js">CDN</a>
        '''
        file("docs/guide/configurationreference.html").text = '<h1 id="config-reference">Configuration Reference</h1>'
        def task = task(docsDir, file("docs/guide/index.html"), file("docs/guide/configurationreference.html"))

        when:
        task.validate()

        then:
        file("report.txt").text.contains("No broken links found.")
    }

    void "fails when a local file target is missing"() {
        given:
        def docsDir = file("docs")
        file("docs/guide/index.html").text = '<a href="missing.html">Missing</a>'
        file("docs/guide/configurationreference.html").text = ''
        def task = task(docsDir, file("docs/guide/index.html"), file("docs/guide/configurationreference.html"))

        when:
        task.validate()

        then:
        thrown(GradleException)
        file("report.txt").text.contains("guide/index.html: link 'missing.html' missing target file guide/missing.html")
    }

    void "fails when a local fragment target is missing"() {
        given:
        def docsDir = file("docs")
        file("docs/guide/index.html").text = '<a href="configurationreference.html#missing">Missing Fragment</a>'
        file("docs/guide/configurationreference.html").text = '<h1 id="config-reference">Configuration Reference</h1>'
        def task = task(docsDir, file("docs/guide/index.html"), file("docs/guide/configurationreference.html"))

        when:
        task.validate()

        then:
        thrown(GradleException)
        file("report.txt").text.contains("guide/index.html: link 'configurationreference.html#missing' missing fragment #missing")
    }

    private ValidateGuideLinksTask task(File docsDir, File... htmlFiles) {
        def project = ProjectBuilder.builder()
                .withProjectDir(testDirectory.toFile())
                .build()
        def task = project.tasks.register("validateGuideLinks", ValidateGuideLinksTask).get()
        task.docsDirectory.set(docsDir)
        task.htmlFiles.from(htmlFiles)
        task.report.set(file("report.txt"))
        task
    }

    private File file(String path) {
        def file = testDirectory.resolve(path).toFile()
        file.parentFile.mkdirs()
        file
    }
}
