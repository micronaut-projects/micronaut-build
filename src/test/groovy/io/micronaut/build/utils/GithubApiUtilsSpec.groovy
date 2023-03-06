package io.micronaut.build.utils


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

}
