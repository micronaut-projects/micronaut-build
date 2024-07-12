package io.micronaut.build.catalogs.tasks

import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import spock.lang.Specification

import static io.micronaut.build.catalogs.tasks.VersionCatalogUpdate.maybeRejectVersionByMinorMajor

class VersionCatalogUpdateTest extends Specification {
    def "test major and minor version extraction"() {
        expect:
        VersionCatalogUpdate.majorVersionOf(version) == expectedMajor
        VersionCatalogUpdate.minorVersionOf(version) == expectedMinor

        where:
        version      | expectedMajor | expectedMinor
        "1.2.3"      | 1             | 2
        "1.2"        | 1             | 2
        "1"          | 1             | 0
        "1.2.3.4"    | 1             | 2
        "3.5-beta"   | 3             | 5
        "3.5-beta1"  | 3             | 5
        "2.1.0-rc1"  | 2             | 1
        "128.256.12" | 128           | 256
        // not semantic versioning, edge cases to make sure
        // the implementation is robust enough
        "abc.def"    | 0             | 0
        "oh.123noes" | 0             | 123
        ""           | 0             | 0
        "wut"        | 0             | 0
    }

    def "tests rejection rules"() {
        def componentSelection = Mock(ComponentSelection)
        def log = Stub(PrintWriter)
        def id = Stub(ModuleComponentIdentifier) {
            getGroup() >> "io.micronaut"
            getModule() >> "micronaut-core"
            getVersion() >> candidateVersion
        }

        when:
        maybeRejectVersionByMinorMajor(
                componentSelection,
                allowMajorUpdate,
                allowMinorUpdate,
                currentVersion,
                candidateVersion,
                log,
                id
        )

        then:
        reject * componentSelection.reject(_)

        where:
        allowMajorUpdate | allowMinorUpdate | currentVersion | candidateVersion | reject
        false            | false            | '1.0.0'        | '2.0.0'          | 1
        false            | false            | '1.0.0'        | '1.1.0'          | 1
        false            | false            | '1.0.0'        | '1.0.1'          | 0
        false            | true             | '1.0.0'        | '1.1.0'          | 0
        false            | true             | '1.0.0'        | '2.0.0'          | 1
        true             | false            | '1.0.0'        | '2.0.0'          | 0
        true             | false            | '1.0.0'        | '1.1.0'          | 1
        true             | false            | '1.0.0'        | '1.0.1'          | 0
        true             | true             | '1.0.0'        | '2.0.0'          | 0
        true             | true             | '1.0.0'        | '1.1.0'          | 0
        true             | true             | '1.0.0'        | '1.0.1'          | 0
    }
}
