package io.micronaut.build.compat

import io.micronaut.build.utils.ExternalURLService
import org.gradle.testfixtures.ProjectBuilder
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.MediaType
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

class FindBaselineTaskTest extends Specification {
    @Shared
    private ClientAndServer mockServer

    def setupSpec() {
        mockServer = ClientAndServer.startClientAndServer()
        mockServer.when(
                request()
                        .withMethod("GET")
                        .withPath("/io/micronaut/micronaut-core/maven-metadata.xml")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.XML_UTF_8)
                        .withBody(FindBaselineTaskTest.getResourceAsStream("/test-maven-metadata.xml").bytes)
        )
    }

    def cleanupSpec() {
        mockServer.stop()
    }

    def "parses releases from GitHub"() {
        def project = ProjectBuilder.builder().build()
        def downloader = ExternalURLService.registerOn(project)
        def task = project.tasks.register("findBaseline", FindBaselineTask) { task ->
            task.groupId.set("io.micronaut")
            task.artifactId.set("micronaut-core")
            task.baseRepository.set("http://localhost:${mockServer.localPort}")
            task.currentVersion.set("2.5.6")
            task.usesService(downloader);
            task.getDownloader().set(downloader)
            task.previousVersion.set(project.layout.buildDirectory.file("baseline.txt"))
        }
        Files.createDirectories(project.file("build").toPath())

        when:
        task.get().execute()

        then:
        def outputFile = project.file("build/baseline.txt")
        outputFile.exists()
        outputFile.text.trim() == "2.5.5"
    }

    def "handles missing resources"() {
        def project = ProjectBuilder.builder().build()
        def downloader = ExternalURLService.registerOn(project)
        def task = project.tasks.register("findBaseline", FindBaselineTask) { task ->
            task.groupId.set("io.micronaut.missing")
            task.artifactId.set("micronaut-missing")
            task.baseRepository.set("http://localhost:${mockServer.localPort}")
            task.currentVersion.set("2.5.6")
            task.usesService(downloader);
            task.getDownloader().set(downloader)
            task.previousVersion.set(project.layout.buildDirectory.file("baseline.txt"))
        }
        Files.createDirectories(project.file("build").toPath())

        when:
        task.get().execute()

        then:
        IllegalStateException e = thrown()
        e.message == "Could not find a previous version for 2.5.6"
    }
}
