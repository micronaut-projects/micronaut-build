package io.micronaut.docs

import spock.lang.Specification

class ReactorJavaApiMacroSpec extends Specification {

    void "reactor:Flux resolves a javadoc link to Flux javadoc"() {
        given:
        JvmLibrary library = new Reactor()

        when:
        Map<String, Object> options = ReactorJavaApiMacro.inlineAnchorOptions(ReactorJavaApiMacro.getBaseUri([:], "reactorapi", library), "Flux", "", "", library)

        then:
        options.type == ':link'
        options.target == 'https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html'
    }
}
