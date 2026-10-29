package io.micronaut.build.compat

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.micronaut.build.utils.ExternalURLService
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Shared
import spock.lang.Specification

import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FindBaselineTaskTest extends Specification {

    @Shared
    private HttpServer mavenRepository

    private static final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>()

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

    def "retries transient failures to download the metadata"() {
        def project = ProjectBuilder.builder().build()
        def task = registerTask(project, "io.micronaut.flaky", "micronaut-flaky")

        when:
        task.get().execute()

        then:
        requests["/io/micronaut/flaky/micronaut-flaky/maven-metadata.xml"].get() == 3
        project.file("build/baseline.txt").text.trim() == "2.5.5"
    }

    def "reports a download that keeps failing"() {
        def project = ProjectBuilder.builder().build()
        def task = registerTask(project, "io.micronaut.broken", "micronaut-broken")
        def url = "http://127.0.0.1:${mavenRepository.address.port}/io/micronaut/broken/micronaut-broken/maven-metadata.xml"

        when:
        task.get().execute()

        then:
        GradleException e = thrown()
        e.message.startsWith("Unable to download $url after 3 attempt(s): ")
        e.message.contains("503")
        requests["/io/micronaut/broken/micronaut-broken/maven-metadata.xml"].get() == 3
        !project.file("build/baseline.txt").exists()
    }

    def "does not retry metadata that does not exist"() {
        def project = ProjectBuilder.builder().build()
        def task = registerTask(project, "io.micronaut.absent", "micronaut-absent")

        when:
        task.get().execute()

        then:
        IllegalStateException e = thrown()
        e.message == "Could not find a previous version for 2.5.6"
        requests["/io/micronaut/absent/micronaut-absent/maven-metadata.xml"].get() == 1
    }

    private TaskProvider<FindBaselineTask> registerTask(Project project, String groupId, String artifactId) {
        def downloader = ExternalURLService.registerOn(project)
        def task = project.tasks.register("findBaseline", FindBaselineTask) { task ->
            task.groupId.set(groupId)
            task.artifactId.set(artifactId)
            task.baseRepository.set("http://127.0.0.1:${mavenRepository.address.port}")
            task.currentVersion.set("2.5.6")
            task.maxAttempts.set(3)
            task.retryBackoff.set(Duration.ofMillis(10))
            task.usesService(downloader)
            task.getDownloader().set(downloader)
            task.previousVersion.set(project.layout.buildDirectory.file("baseline.txt"))
        }
        Files.createDirectories(project.file("build").toPath())
        task
    }

    private static void serveMavenMetadata(HttpExchange exchange) throws IOException {
        String path = exchange.requestURI.path
        int count = requests.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet()
        try {
            if (path == "/io/micronaut/broken/micronaut-broken/maven-metadata.xml"
                || (path == "/io/micronaut/flaky/micronaut-flaky/maven-metadata.xml" && count < 3)) {
                exchange.sendResponseHeaders(503, -1)
            } else if (path == "/io/micronaut/flaky/micronaut-flaky/maven-metadata.xml") {
                byte[] response = FindBaselineTaskTest.getResourceAsStream("/test-maven-metadata.xml").bytes
                exchange.sendResponseHeaders(200, response.length)
                exchange.responseBody.write(response)
            } else if (exchange.requestMethod == "GET" && exchange.requestURI.path == "/io/micronaut/micronaut-core/maven-metadata.xml") {
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
