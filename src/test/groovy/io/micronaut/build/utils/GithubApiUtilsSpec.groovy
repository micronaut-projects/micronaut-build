package io.micronaut.build.utils

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import spock.lang.Specification

class GithubApiUtilsSpec extends Specification {

    void "it is possible to fetch tags"() {
        when:
        String tags = new String(GithubApiUtils.fetchTagsFromGitHub(Stub(Logger), "micronaut-projects/micronaut-security"), "UTF-8")

        then:
        noExceptionThrown()
        tags.contains("v")
    }

    void "it is possible to fetch releases"() {
        when:
        String releases = new String(GithubApiUtils.fetchReleasesFromGitHub(Stub(Logger), "micronaut-projects/micronaut-security"), "UTF-8")

        then:
        noExceptionThrown()
        releases.contains("3.")
    }

    void "reports error"() {
        given:
        def logger = Mock(Logger)
        when:
        GithubApiUtils.fetchReleasesFromGitHub(logger, "micronaut-projects/nope")

        then:
        GradleException ex = thrown()
        1 * logger.error(_) >> {
            String message = it[0]
            assert message.startsWith("Failed to read from Github API. Response code: 404")
        }
    }

}
