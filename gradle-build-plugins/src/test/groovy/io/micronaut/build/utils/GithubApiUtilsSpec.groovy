package io.micronaut.build.utils

import org.gradle.api.logging.Logger
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.MediaType
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

@RestoreSystemProperties
class GithubApiUtilsSpec extends Specification {

    @Shared
    private ClientAndServer mockServer

    def setupSpec() {
        mockServer = ClientAndServer.startClientAndServer()
        ['tags', 'releases'].each { what ->
            mockServer.when(
                    request()
                            .withMethod("GET")
                            .withPath("/repos/micronaut-projects/micronaut-security/$what")
            ).respond(
                    response()
                            .withStatusCode(200)
                            .withContentType(MediaType.JSON_UTF_8)
                            .withBody(GithubApiUtilsSpec.getResourceAsStream("/io.micronaut.build.utils/releases.json").bytes)
            )
            mockServer.when(
                    request()
                            .withMethod("GET")
                            .withPath("/repos/micronaut-projects/nope/$what")
            ).respond(
                    response()
                            .withStatusCode(404)
                            .withBody("Not found")
            )
        }

        System.setProperty(GithubApiUtils.GITHUB_API_BASE_URL_SYSTEM_PROPERTY, "http://localhost:${mockServer.localPort}")
    }

    def cleanupSpec() {
        mockServer.stop()
    }

    void "it is possible to fetch tags"() {
        when:
        String tags = new String(GithubApiUtils.fetchTagsFromGitHub(Stub(Logger), "micronaut-projects/micronaut-security"), "UTF-8")

        then:
        noExceptionThrown()
        tags.contains("v")
    }


}
