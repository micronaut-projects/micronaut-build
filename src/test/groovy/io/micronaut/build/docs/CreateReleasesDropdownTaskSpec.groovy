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
        given:
        String slug = "micronaut-projects/micronaut-liquibase"

        when:
        String url = "https://api.github.com/repos/${slug}/tags"
        String json = new URL(url).text

        then:
        noExceptionThrown()

        when:
        String html = CreateReleasesDropdownTask.composeSelectHtml(json, "micronaut-projects/micronaut-liquibase", "5.6.0")

        then:
        noExceptionThrown()
        html.contains("<option value='https://micronaut-projects.github.io/micronaut-liquibase/5.5.0/guide/index.html'>5.5.0</option>")
    }
}
