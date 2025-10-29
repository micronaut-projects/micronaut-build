package io.micronaut.build.catalogs.internal


import spock.lang.Specification
import spock.lang.Unroll

class StatusTest extends Specification {
    @Unroll("status of #version is #status")
    def "detects appropriate status"() {
        expect:
        Status.detectStatus(version) == status

        where:
        version           | status
        '1'               | Status.RELEASE
        '1.2'             | Status.RELEASE
        '1.2-rc'          | Status.RELEASE_CANDIDATE
        '1.2-rc-1'        | Status.RELEASE_CANDIDATE
        '1.2-rc.1'        | Status.RELEASE_CANDIDATE
        '1.2-rc1'         | Status.RELEASE_CANDIDATE
        '5.6.0.Beta1'     | Status.BETA
        '5.6.0-B1'        | Status.BETA
        '5.6.0.alpha.1'   | Status.ALPHA
        '5.6.0.rc'        | Status.RELEASE_CANDIDATE
        '5.6.0.cr2'       | Status.RELEASE_CANDIDATE
        '3.4.m'           | Status.MILESTONE
        '3.4.milestone-2' | Status.MILESTONE
        '3.4-snapshot'    | Status.INTEGRATION
    }
}
