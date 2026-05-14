package io.micronaut.docs

import io.micronaut.docs.javadoc.JvmLibrary
import io.micronaut.docs.javadoc.RxJava
import io.micronaut.docs.macros.RxJavaApiMacro
import spock.lang.Specification

class RxJavaApiMacroSpec extends Specification {

    void "rx:Flowable resolves a javadoc link to Flowable"() {
        given:
        JvmLibrary library = new RxJava()

        when:
        Map<String, Object> options = RxJavaApiMacro.inlineAnchorOptions(RxJavaApiMacro.getBaseUri([:], "rxapi", library), "Flowable", "", "", library)

        then:
        options.type == ':link'
        options.target == 'http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Flowable.html'
    }
}
