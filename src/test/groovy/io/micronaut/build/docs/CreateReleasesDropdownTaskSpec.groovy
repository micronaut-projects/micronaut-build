package io.micronaut.build.docs

import spock.lang.Specification

class CreateReleasesDropdownTaskSpec extends Specification {

    void "IOException is catched"() {
        when:
        String html = CreateReleasesDropdownTask.composeSelectHtml("micronaut-projects/micronaut-liquibase", "5.6.0", url -> {
            throw new IOException("Server returned HTTP response code: 403 for URL: https://api.github.com/repos/micronaut-projects/micronaut-liquibase/tags")
        })

        then:
        noExceptionThrown()
        !html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/5.5.0/guide/index.html'>5.5.0</option>")
        html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/latest/guide/index.html'>LATEST</option>")
        html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/snapshot/guide/index.html'>SNAPSHOT</option>")

    }
    void "options are correctly populated"() {
        when:
        String html = CreateReleasesDropdownTask.composeSelectHtml("micronaut-projects/micronaut-liquibase", "5.6.0")

        then:
        noExceptionThrown()
        html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/5.5.0/guide/index.html'>5.5.0</option>")
    }
}
