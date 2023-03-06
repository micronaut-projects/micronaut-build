package io.micronaut.build.quality

import io.micronaut.build.AbstractFunctionalTest

class QualityReportingPluginFunctionalTest extends AbstractFunctionalTest {

    void "it can run aggregate coverage reports"() {
        given:
        withSample("test-micronaut-module")
        file("gradle.properties") << "micronaut.jacoco.enabled=true"

        when:
        run 'testCodeCoverageReport'

        then:
        tasks {
            succeeded ':subproject1:test'
            succeeded ':subproject2:test'
            succeeded ':testCodeCoverageReport'
        }
        file("build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml").exists()
    }

    void "it can run Spotless and Checkstyle"() {
        given:
        withSample("test-micronaut-module")

        when:
        run 'spotlessApply', 'checkstyleMain'

        then:
        tasks {
            succeeded ':subproject1:spotlessCheck'
            succeeded ':subproject1:checkstyleMain'

            succeeded ':subproject2:spotlessCheck'
            succeeded ':subproject2:checkstyleMain'
        }
        file("subproject1/build/reports/checkstyle/main.xml").exists()
        file("subproject2/build/reports/checkstyle/main.xml").exists()
    }

}
