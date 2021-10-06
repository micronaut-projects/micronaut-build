package io.micronaut.build

import spock.lang.Specification
import spock.lang.Unroll

class MicronautBuildExtensionSpec extends Specification {

    @Unroll
    void "dependencyUpdatesPattern excludes non GA version: #version"(String version, boolean expectedMatch) {
        given:
        String pattern = new MicronautBuildExtension(null).dependencyUpdatesPattern

        when:
        boolean matches = version ==~ pattern

        then:
        matches == expectedMatch

        where:
        version             | expectedMatch
        "1.2.3"             | false
        "11.0.1.Final"      | false
        "1.2.3-b07"         | true
        "1.2.3-rc11"        | true
        "1.2.3-RC1"         | true
        "12.0.0.Dev01"      | true
        "1.4.0-rc"          | true
    }

}
