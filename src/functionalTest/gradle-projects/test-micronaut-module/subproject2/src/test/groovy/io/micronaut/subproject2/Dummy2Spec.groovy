package io.micronaut.subproject2

import spock.lang.Specification

class Dummy2Spec extends Specification {

    void "test it works"() {
        expect:
        new Dummy2().itWorks()
    }

}
