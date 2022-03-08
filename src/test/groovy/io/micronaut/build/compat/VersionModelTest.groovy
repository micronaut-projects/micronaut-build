package io.micronaut.build.compat

import spock.lang.Specification

class VersionModelTest extends Specification {
    def "compares versions"() {
        expect:
        v("1.0") == v("1.0")
        v("1.0") < v("1.1")
        v("1.2") > v("1.1")
        v("1.1.1") > v("1.1")
        v("1") < v("1.1")
        v("1.0") < v("1.0.1")
        v("0.9") < v("0.10")
        v("0.1") < v("1.3.2")
    }

    static VersionModel v(String version) {
        VersionModel.of(version)
    }
}
