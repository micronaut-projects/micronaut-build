package io.micronaut.build.catalogs.tasks


import spock.lang.Specification

import static io.micronaut.build.catalogs.tasks.VersionCatalogUpdate.maybeRejectVersionByMinorMajor
import static io.micronaut.build.utils.VersionParser.parse

class VersionCatalogUpdateTest extends Specification {

    def "tests rejection rules"() {
        def rules = Mock(VersionCatalogUpdate.CandidateDetails)
        def id = "io.micronaut:micronaut-core"

        when:
        maybeRejectVersionByMinorMajor(
                allowMajorUpdate,
                allowMinorUpdate,
                parse(currentVersion),
                parse(candidateVersion)
                ,
                rules
        )

        then:
        reject * rules.rejectCandidate(_)

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
