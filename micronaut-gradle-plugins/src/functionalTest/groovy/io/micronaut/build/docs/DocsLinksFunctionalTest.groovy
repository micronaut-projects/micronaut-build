package io.micronaut.build.docs

import io.micronaut.build.AbstractFunctionalTest

class DocsLinksFunctionalTest extends AbstractFunctionalTest {

    void "check docs links task is available but not part of docs task"() {
        given:
        withSample("test-micronaut-module")

        when:
        run "tasks", "--all"

        then:
        outputContains("checkDocsLinks")

        when:
        run "docs", "--dry-run"

        then:
        outputContains(":docs SKIPPED")
        outputDoesNotContain(":checkDocsLinks")

        when:
        run "checkDocsLinks", "--dry-run"

        then:
        outputContains(":assembleFinalDocs SKIPPED")
        outputContains(":checkDocsLinks SKIPPED")
    }
}
