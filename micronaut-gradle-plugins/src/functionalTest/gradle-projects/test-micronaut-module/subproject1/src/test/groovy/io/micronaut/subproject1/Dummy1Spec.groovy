package io.micronaut.subproject1

import spock.lang.Specification

class Dummy1Spec extends Specification {

    void "test it works"() {
        expect:
        new Dummy1().itWorks()
    }

}
