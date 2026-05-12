package io.micronaut.build.pom

import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class PomDownloaderTest extends Specification {
    @TempDir
    Path tmpDir

    private HttpServer server

    def cleanup() {
        server?.stop(0)
    }

    def "retries rate limited POM downloads"() {
        given:
        def attempts = new AtomicInteger()
        server = startServer { exchange ->
            attempts.incrementAndGet()
            if (attempts.get() == 1) {
                exchange.responseHeaders.add('Retry-After', '0')
                exchange.sendResponseHeaders(429, -1)
            } else {
                byte[] body = '<project/>'.getBytes(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, body.length)
                exchange.responseBody.withCloseable { it.write(body) }
            }
        }
        def downloader = new PomDownloader([serverUrl()], tmpDir.toFile())

        when:
        def result = downloader.tryDownloadPom(new PomDependency(false, 'example', 'library', '1.0.0', 'compile'))

        then:
        result.present
        result.get().text == '<project/>'
        attempts.get() == 2
    }

    def "reports rate limiting as transient failure instead of missing dependency"() {
        given:
        server = startServer { exchange ->
            exchange.responseHeaders.add('Retry-After', '0')
            exchange.sendResponseHeaders(429, -1)
        }
        def downloader = new PomDownloader([serverUrl()], tmpDir.toFile())

        when:
        downloader.tryDownloadPom(new PomDependency(false, 'example', 'library', '1.0.0', 'compile'))

        then:
        def ex = thrown(GradleException)
        ex.message.contains('Unable to download POM')
    }

    def "returns empty for missing POMs"() {
        given:
        server = startServer { exchange ->
            exchange.sendResponseHeaders(404, -1)
        }
        def downloader = new PomDownloader([serverUrl()], tmpDir.toFile())

        expect:
        downloader.tryDownloadPom(new PomDependency(false, 'example', 'library', '1.0.0', 'compile')).empty
    }

    private HttpServer startServer(Closure handler) {
        def httpServer = HttpServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0)
        httpServer.createContext('/') { exchange -> handler.call(exchange) }
        httpServer.start()
        httpServer
    }

    private String serverUrl() {
        "http://${server.address.hostString}:${server.address.port}"
    }
}
