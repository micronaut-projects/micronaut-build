package io.micronaut.build.catalogs.tasks

import io.micronaut.build.utils.ComparableVersion
import io.micronaut.build.utils.VersionParser
import spock.lang.Specification

class VersionParserTest extends Specification {
    void "parses semantic versions"() {
        given:
        def version = v(input)

        expect:
        version.fullVersion() == input
        version.major().orElse(null) == major
        version.minor().orElse(null) == minor
        version.patch().orElse(null) == patch
        version.qualifier().orElse(null) == qualifier
        version.qualifierVersion().orElse(null) == qualifierVersion
        version.getExtraVersions() == extraVersions
        version.getVersionComponents() == allVersions

        where:
        input                                            | major | minor | patch | qualifier  | qualifierVersion | extraVersions | allVersions
        "1"                                              | 1     | null  | null  | null       | null             | []            | [1, 0, 0]
        "1.0"                                            | 1     | 0     | null  | null       | null             | []            | [1, 0, 0]
        "11.0"                                           | 11    | 0     | null  | null       | null             | []            | [11, 0, 0]
        "11.3.0"                                         | 11    | 3     | 0     | null       | null             | []            | [11, 3, 0]
        "11.3.1"                                         | 11    | 3     | 1     | null       | null             | []            | [11, 3, 1]
        "1-SNAPSHOT"                                     | 1     | null  | null  | "SNAPSHOT" | null             | []            | [1, 0, 0]
        "1.0-beta"                                       | 1     | 0     | null  | "beta"     | null             | []            | [1, 0, 0]
        "1.0-beta2"                                      | 1     | 0     | null  | "beta"     | 2                | []            | [1, 0, 0]
        "1.5.7-beta-33"                                  | 1     | 5     | 7     | "beta"     | 33               | []            | [1, 5, 7]
        "1.5.7.foo"                                      | 1     | 5     | 7     | "foo"      | null             | []            | [1, 5, 7]
        "1.5.7.4"                                        | 1     | 5     | 7     | null       | null             | [4]           | [1, 5, 7, 4]
        "1.5.7.45.0.1"                                   | 1     | 5     | 7     | null       | null             | [45, 0, 1]    | [1, 5, 7, 45, 0, 1]
        "1.5.7.45.0.1-SNAPSHOT"                          | 1     | 5     | 7     | "SNAPSHOT" | null             | [45, 0, 1]    | [1, 5, 7, 45, 0, 1]
        "1.1.0-9f31d6308e7ebbc3d7904b64ebb9f61f7e22a968" | 1     | 1     | 0     | null       | null             | []            | [1, 1, 0]
        // The next items are not semantic versions, and we do best effort to return something reasonable. For example, if we considered
        // that the qualifier for "1.0.0-jdk8" is "jdk8", then how do you make a difference with "1.2.0-beta8", where actually the qualifier is "beta"
        // and the qualifier version is "8"? Instead of adding a dozen special cases, the parser will simply do best effort.
        "2.2-pre-release-emit-jdk8-version.1"            | 2     | 2     | null  | "pre"       | null             | []            | [2, 2, 0]
        ".8.2"                                           | 0     | 8     | 2     | null       | null             | []            | [0, 8, 2]
    }

    def "compares versions"() {
        expect:
        verifyAll {
            v("1.0") == v("1")
            v("1.0") > v("0.9")
            v("1.0.1") > v("1.0")
            v("1.1.0") > v("1.0.0")
            v("1.0") < v("2.0")
            v("1.0.0.1") > v("1.0.0")
            v("1.0.0") > v("1.0.0-beta-1")
            v("0.5") > v("0.0.5")
            v("1.0.0-alpha") < v("1.0.0-beta")
            v("1.0.0-beta") < v("1.0.0-rc")
            v("1.0.0-rc") < v("1.0.0-final")
            v("1.0.0-final") < v("1.0.0")
            v("1.0.0-beta-1") < v("1.0.0-beta-2")
            v("unknown") < v("1.0")
            v("1.22") > v("1.21")
            v("1.1.1") > v("1.1")
            v("1") < v("1.1")
            v("1.0") < v("1.0.1")
            v("0.9") < v("0.10")
            v("0.1") < v("1.3.2")
            v("3.4.5") < v("3.5.0")
            v("3.5.0") > v("3.4.5")
            v("4.0.0-M1") < v("4.0.0-M2")
        }
    }

    private static ComparableVersion v(String version) {
        return VersionParser.parse(version)
    }
}
