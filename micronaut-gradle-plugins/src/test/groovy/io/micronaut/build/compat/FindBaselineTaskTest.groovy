package io.micronaut.build.compat

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.micronaut.build.utils.ExternalURLService
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Shared
import spock.lang.Specification

import java.net.InetSocketAddress
import java.nio.file.Files

class FindBaselineTaskTest extends Specification {

    @Shared
    private HttpServer mavenRepository

    def setupSpec() {
        mavenRepository = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        mavenRepository.createContext("/", FindBaselineTaskTest::serveMavenMetadata)
        mavenRepository.start()
    }

    def cleanupSpec() {
        mavenRepository.stop(0)
    }

    def "parses releases from Maven Central"() {
        def project = ProjectBuilder.builder().build()
        def downloader = ExternalURLService.registerOn(project)
        def task = project.tasks.register("findBaseline", FindBaselineTask) { task ->
            task.groupId.set("io.micronaut")
            task.artifactId.set("micronaut-core")
            task.baseRepository.set("http://127.0.0.1:${mavenRepository.address.port}")
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
            task.baseRepository.set("http://127.0.0.1:${mavenRepository.address.port}")
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

    private static void serveMavenMetadata(HttpExchange exchange) throws IOException {
        try {
            if (exchange.requestMethod == "GET" && exchange.requestURI.path == "/io/micronaut/micronaut-core/maven-metadata.xml") {
                byte[] response = FindBaselineTaskTest.getResourceAsStream("/test-maven-metadata.xml").bytes
                exchange.responseHeaders.add("Content-Type", "application/xml; charset=utf-8")
                exchange.sendResponseHeaders(200, response.length)
                exchange.responseBody.write(response)
            } else {
                exchange.sendResponseHeaders(404, -1)
            }
        } finally {
            exchange.close()
        }
    }
}
