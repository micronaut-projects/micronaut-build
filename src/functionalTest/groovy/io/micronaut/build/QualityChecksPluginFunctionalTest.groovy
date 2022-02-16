package io.micronaut.build

class QualityChecksPluginFunctionalTest extends AbstractFunctionalTest {

    void "it can run aggregate coverage reports"() {
        given:
        withSample("test-micronaut-module")

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

}
