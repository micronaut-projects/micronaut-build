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

    void "the JaCoCo agent only instruments Micronaut classes by default"() {
        given:
        withSample("test-micronaut-module")
        file("gradle.properties") << "micronaut.jacoco.enabled=true"
        file("subproject1/build.gradle") << """
            tasks.register("printJacocoIncludes") {
                def includes = providers.provider { tasks.test.jacoco.includes }
                doLast { println "JaCoCo includes: \${includes.get()}" }
            }
        """

        when:
        run 'printJacocoIncludes'

        then:
        outputContains 'JaCoCo includes: [io.micronaut.*]'
    }

    void "a project can choose which classes the JaCoCo agent instruments"() {
        given:
        withSample("test-micronaut-module")
        file("gradle.properties") << "micronaut.jacoco.enabled=true"
        file("subproject1/build.gradle") << """
            tasks.named("test") {
                jacoco {
                    includes = ["io.micronaut.subproject1.*", "org.example.*"]
                }
            }
            tasks.register("printJacocoIncludes") {
                def includes = providers.provider { tasks.test.jacoco.includes }
                doLast { println "JaCoCo includes: \${includes.get()}" }
            }
        """

        when:
        run 'printJacocoIncludes'

        then:
        outputContains 'JaCoCo includes: [io.micronaut.subproject1.*, org.example.*]'
    }

    void "SonarCloud raises no issue on test sources, which stay test sources"() {
        given:
        withSample("test-micronaut-module")
        withSonarPropertiesTask()

        when:
        run 'printSonarProperties'

        then:
        outputContains 'SONAR sonar.issue.ignore.multicriteria=micronautTestSources'
        outputContains 'SONAR sonar.issue.ignore.multicriteria.micronautTestSources.ruleKey=*'
        outputContains 'SONAR sonar.issue.ignore.multicriteria.micronautTestSources.resourceKey=**/src/test/**'
        def subproject1 = file("subproject1").canonicalFile
        def sonarProperties = result.output.readLines()
            .findAll { it.startsWith('SONAR ') }
            .collectEntries { it.substring('SONAR '.length()).split('=', 2) as List }
        def directories = { String key -> (sonarProperties[key] as String).split(',').collect { new File(it).canonicalFile } }
        // The Groovy test sources stay test sources, which coverage needs, and never become main ones
        directories(':subproject1.sonar.tests').contains(new File(subproject1, 'src/test/groovy'))
        directories(':subproject1.sonar.sources').every { !it.path.contains("${File.separator}src${File.separator}test") }
    }

    void "the test sources criterion adds to the criteria a project declares"() {
        given:
        withSample("test-micronaut-module")
        withSonarPropertiesTask()
        buildFile << """
            sonar {
                properties {
                    property "sonar.issue.ignore.multicriteria", "e1"
                    property "sonar.issue.ignore.multicriteria.e1.ruleKey", "java:S1192"
                    property "sonar.issue.ignore.multicriteria.e1.resourceKey", "**/Generated*.java"
                }
            }
        """

        when:
        run 'printSonarProperties'

        then:
        outputContains 'SONAR sonar.issue.ignore.multicriteria=e1,micronautTestSources'
        outputContains 'SONAR sonar.issue.ignore.multicriteria.e1.ruleKey=java:S1192'
        outputContains 'SONAR sonar.issue.ignore.multicriteria.micronautTestSources.resourceKey=**/src/test/**'
    }

    void "a subproject that declares its own criteria keeps the test sources criterion"() {
        given:
        withSample("test-micronaut-module")
        withSonarPropertiesTask()
        // The root applies the Sonar plugin after the subprojects are evaluated
        file("subproject1/build.gradle") << """
            rootProject.pluginManager.withPlugin("org.sonarqube") {
                sonar {
                    properties {
                        property "sonar.issue.ignore.multicriteria", "s1"
                        property "sonar.issue.ignore.multicriteria.s1.ruleKey", "java:S1192"
                        property "sonar.issue.ignore.multicriteria.s1.resourceKey", "**/Generated*.java"
                    }
                }
            }
        """

        when:
        run 'printSonarProperties'

        then:
        outputContains 'SONAR :subproject1.sonar.issue.ignore.multicriteria=s1,micronautTestSources'
        outputContains 'SONAR :subproject1.sonar.issue.ignore.multicriteria.s1.ruleKey=java:S1192'
        outputContains 'SONAR :subproject1.sonar.issue.ignore.multicriteria.micronautTestSources.ruleKey=*'
        outputContains 'SONAR :subproject1.sonar.issue.ignore.multicriteria.micronautTestSources.resourceKey=**/src/test/**'
        outputContains 'SONAR sonar.issue.ignore.multicriteria=micronautTestSources'
        // A subproject that declares no criteria inherits the root's
        outputDoesNotContain ':subproject2.sonar.issue.ignore'
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

    private void withSonarPropertiesTask() {
        environment.SONAR_TOKEN = "dummy"
        buildFile << """
            tasks.register("printSonarProperties") {
                def sonarProperties = tasks.named("sonar").flatMap { it.properties }
                doLast {
                    sonarProperties.get().findAll { k, v -> k.contains("sonar.issue.ignore") || k.endsWith("sonar.sources") || k.endsWith("sonar.tests") }
                        .sort()
                        .each { k, v -> println "SONAR \${k}=\${v}" }
                }
            }
        """
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
