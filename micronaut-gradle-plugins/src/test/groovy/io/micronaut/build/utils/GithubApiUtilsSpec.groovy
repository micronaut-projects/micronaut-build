package io.micronaut.build.utils

import org.gradle.api.logging.Logger
import software.xdev.mockserver.client.MockServerClient
import software.xdev.mockserver.model.MediaType
import software.xdev.mockserver.netty.MockServer
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import java.nio.charset.StandardCharsets

import static software.xdev.mockserver.model.HttpRequest.request
import static software.xdev.mockserver.model.HttpResponse.response

@RestoreSystemProperties
class GithubApiUtilsSpec extends Specification {

    @Shared
    private MockServer mockServer
    @Shared
    private MockServerClient mockServerClient

    def setupSpec() {
        mockServer = new MockServer()
        mockServerClient = new MockServerClient("localhost", mockServer.localPort)
        ['tags', 'releases'].each { what ->
            mockServerClient.when(
                    request()
                            .withMethod("GET")
                            .withPath("/repos/micronaut-projects/micronaut-security/$what")
            ).respond(
                    response()
                            .withStatusCode(200)
                            .withContentType(MediaType.JSON_UTF_8)
                            .withBody(GithubApiUtilsSpec.getResourceAsStream("/io.micronaut.build.utils/releases.json").bytes)
            )
            mockServerClient.when(
                    request()
                            .withMethod("GET")
                            .withPath("/repos/micronaut-projects/nope/$what")
            ).respond(
                    response()
                            .withStatusCode(404)
                            .withBody("Not found")
            )
            System.setProperty(GithubApiUtils.GITHUB_API_BASE_URL_SYSTEM_PROPERTY, "http://localhost:${mockServer.localPort}")
        }
    }

    def cleanupSpec() {
        mockServerClient.close()
        mockServer.close()
    }

    void "it is possible to fetch tags"() {

        when:
        var tags = new String(GithubApiUtils.fetchTagsFromGitHub(Stub(Logger), "micronaut-projects/micronaut-security"), StandardCharsets.UTF_8)

        then:
        noExceptionThrown()
        tags.contains("v")
    }
}
