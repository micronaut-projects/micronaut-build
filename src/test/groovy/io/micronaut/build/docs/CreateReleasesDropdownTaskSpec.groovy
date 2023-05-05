package io.micronaut.build.docs

import spock.lang.Specification

class CreateReleasesDropdownTaskSpec extends Specification {

    void "empty JSON array returned when error fetching JSON is handled correctly"() {
        when:
        String html = CreateReleasesDropdownTask.composeSelectHtml("[]", "micronaut-projects/micronaut-liquibase", "5.6.0")

        then:
        noExceptionThrown()
        !html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/5.5.0/guide/index.html'>5.5.0</option>")
        html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/latest/guide/index.html'>LATEST</option>")
        html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/snapshot/guide/index.html'>SNAPSHOT</option>")

    }
    void "options are correctly populated"() {
        String json = """[
  {
    "name": "v5.7.2",
    "zipball_url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/zipball/refs/tags/v5.7.2",
    "tarball_url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/tarball/refs/tags/v5.7.2",
    "commit": {
      "sha": "7095bcf7f0fed09411da2573c653836cba212962",
      "url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/commits/7095bcf7f0fed09411da2573c653836cba212962"
    },
    "node_id": "MDM6UmVmMTU4MjExNzk4OnJlZnMvdGFncy92NS43LjI="
  },
  {
    "name": "v5.7.1",
    "zipball_url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/zipball/refs/tags/v5.7.1",
    "tarball_url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/tarball/refs/tags/v5.7.1",
    "commit": {
      "sha": "ad226fa161d14929f5ae36b25c338d2f3072e3e0",
      "url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/commits/ad226fa161d14929f5ae36b25c338d2f3072e3e0"
    },
    "node_id": "MDM6UmVmMTU4MjExNzk4OnJlZnMvdGFncy92NS43LjE="
  },
  {
    "name": "v5.7.0",
    "zipball_url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/zipball/refs/tags/v5.7.0",
    "tarball_url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/tarball/refs/tags/v5.7.0",
    "commit": {
      "sha": "36341fa043716360a9cf044e9f902e0c399cd8f8",
      "url": "https://api.github.com/repos/micronaut-projects/micronaut-liquibase/commits/36341fa043716360a9cf044e9f902e0c399cd8f8"
    },
    "node_id": "MDM6UmVmMTU4MjExNzk4OnJlZnMvdGFncy92NS43LjA="
  }
]
"""

        when:
        String html = CreateReleasesDropdownTask.composeSelectHtml(json, "micronaut-projects/micronaut-liquibase", "5.7.0")

        then:
        noExceptionThrown()
        html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/5.7.1/guide/index.html'>5.7.1</option>")
    }
}
