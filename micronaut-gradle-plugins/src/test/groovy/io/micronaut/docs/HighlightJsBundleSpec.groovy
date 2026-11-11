package io.micronaut.docs

import spock.lang.Specification

class HighlightJsBundleSpec extends Specification {

    void "docs highlighter bundle registers Scala"() {
        when:
        String bundle = new File("src/main/template/js/highlight.pack.js").text

        then:
        bundle.contains('hljs.registerLanguage("scala"')
    }
}
