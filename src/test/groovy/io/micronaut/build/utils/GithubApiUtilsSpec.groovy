package io.micronaut.build.utils

import spock.lang.Specification

class GithubApiUtilsSpec extends Specification {

    void "it is possible to fetch tags"() {
        when:
        String tags = new String(GithubApiUtils.fetchTagsFromGitHub(null, "micronaut-projects/micronaut-security"), "UTF-8")

        then:
        noExceptionThrown()
        tags.contains("v")
    }

    void "it is possible to fetch releases"() {
        when:
        String releases = new String(GithubApiUtils.fetchReleasesFromGitHub(null, "micronaut-projects/micronaut-security"), "UTF-8")

        then:
        noExceptionThrown()
        releases.contains("3.")
    }

}
