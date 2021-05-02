package io.micronaut.docs

import spock.lang.Specification

class CreateReleasesDropDownTaskSpec extends Specification {

    void "for micronaut-projects/micronaut-core docs.micronaut.io is used not gihtub pages url"() {
        given:
        String html = CreateReleasesDropdownTask.composeSelectHtml('micronaut-projects/micronaut-core', '2.0.1')
        expect:
        html.contains('https://docs.micronaut.io/snapshot/guide/index.html')
        !html.contains('https://micronaut-projects.github.io/micronaut-core/snapshot/guide/index.html')
    }

}