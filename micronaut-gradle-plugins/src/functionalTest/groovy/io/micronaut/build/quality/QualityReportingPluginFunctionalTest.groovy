package io.micronaut.build.quality

import io.micronaut.build.AbstractFunctionalTest

import org.w3c.dom.Element
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

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
        def report = aggregateJacocoReport()
        report.exists()
        def branchCoverage = methodBranchCoverage(report, 'io/micronaut/subproject1/Dummy1', 'isMapHelper')
        branchCoverage.missed == '0'
        branchCoverage.covered == '2'
    }

    void "jacocoTestReport runs aggregate coverage reports"() {
        given:
        withSample("test-micronaut-module")
        file("gradle.properties") << "micronaut.jacoco.enabled=true"

        when:
        run 'jacocoTestReport'

        then:
        tasks {
            succeeded ':subproject1:test'
            succeeded ':subproject2:test'
            succeeded ':testCodeCoverageReport'
            succeeded ':jacocoTestReport'
        }
        aggregateJacocoReport().exists()
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

    private File aggregateJacocoReport() {
        file("build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml")
    }

    private static Map<String, String> methodBranchCoverage(File report, String className, String methodName) {
        def documentBuilderFactory = DocumentBuilderFactory.newInstance()
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        documentBuilderFactory.setXIncludeAware(false)
        documentBuilderFactory.setExpandEntityReferences(false)
        def documentBuilder = documentBuilderFactory.newDocumentBuilder()
        documentBuilder.setEntityResolver({ String publicId, String systemId ->
            new InputSource(new StringReader(""))
        } as EntityResolver)
        def document = documentBuilder.parse(report)

        def classes = document.getElementsByTagName("class")
        for (int i = 0; i < classes.length; i++) {
            Element clazz = classes.item(i) as Element
            if (clazz.getAttribute("name") == className) {
                def methods = clazz.getElementsByTagName("method")
                for (int j = 0; j < methods.length; j++) {
                    Element method = methods.item(j) as Element
                    if (method.getAttribute("name") == methodName) {
                        def counters = method.getElementsByTagName("counter")
                        for (int k = 0; k < counters.length; k++) {
                            Element counter = counters.item(k) as Element
                            if (counter.getAttribute("type") == "BRANCH") {
                                return [
                                    missed: counter.getAttribute("missed"),
                                    covered: counter.getAttribute("covered")
                                ]
                            }
                        }
                    }
                }
            }
        }
        throw new AssertionError("Could not find branch coverage for $className#$methodName")
    }
}
