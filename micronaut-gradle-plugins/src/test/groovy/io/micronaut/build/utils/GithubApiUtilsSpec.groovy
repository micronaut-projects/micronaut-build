package io.micronaut.build.utils

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.api.logging.Logger
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

@RestoreSystemProperties
class GithubApiUtilsSpec extends Specification {

    @Shared
    private HttpServer githubApi

    def setupSpec() {
        githubApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        githubApi.createContext("/", GithubApiUtilsSpec::serveGithubApiResponse)
        githubApi.start()
        System.setProperty(GithubApiUtils.GITHUB_API_BASE_URL_SYSTEM_PROPERTY, "http://127.0.0.1:${githubApi.address.port}")
    }

    def cleanupSpec() {
        githubApi.stop(0)
    }

    void "it is possible to fetch tags"() {

        when:
        var tags = new String(GithubApiUtils.fetchTagsFromGitHub(Stub(Logger), "micronaut-projects/micronaut-security"), StandardCharsets.UTF_8)

        then:
        noExceptionThrown()
        tags.contains("v")
    }

    private static void serveGithubApiResponse(HttpExchange exchange) throws IOException {
        try {
            if (exchange.requestMethod == "GET" && ["/repos/micronaut-projects/micronaut-security/tags", "/repos/micronaut-projects/micronaut-security/releases"].contains(exchange.requestURI.path)) {
                byte[] response = GithubApiUtilsSpec.getResourceAsStream("/io.micronaut.build.utils/releases.json").bytes
                exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(200, response.length)
                exchange.responseBody.write(response)
            } else {
                byte[] response = "Not found".bytes
                exchange.sendResponseHeaders(404, response.length)
                exchange.responseBody.write(response)
            }
        } finally {
            exchange.close()
        }
    }
}
