package io.micronaut.build.catalogs

import groovy.xml.XmlSlurper
import io.micronaut.build.AbstractFunctionalTest
import spock.lang.Issue
import spock.lang.Unroll

class BomGenerationFunctionalTest extends AbstractFunctionalTest {
    def "inlines Micronaut catalogs into the generated catalog"() {

        given:
        withSample("test-bom-module")

        when:
        run 'publishAllPublicationsToBuildRepository'

        then:
        def moduleDir = file("build/repo/io/micronaut/dummy/micronaut-test-bom-module/1.2.3")
        def catalogFile = new File(moduleDir, "micronaut-test-bom-module-1.2.3.toml")
        moduleDir.exists()
        catalogFile.exists()
        matchesTestBomModuleCatalog(catalogFile, true)

        and:
        def pomFile = new File(moduleDir, "micronaut-test-bom-module-1.2.3.pom")
        matchesTestBomModulePom(pomFile, true)
    }

    def "can restrict the set of inlined aliases in a catalog"() {

        given:
        withSample("test-bom-module")
        buildFile << """
            micronautBom {
                inlinedAliases.put("micronaut-aws-bom", 
                    ["aws-lambda*"] as Set
                )
            }
        """

        when:
        run 'publishAllPublicationsToBuildRepository'

        then:
        def moduleDir = file("build/repo/io/micronaut/dummy/micronaut-test-bom-module/1.2.3")
        def catalogFile = new File(moduleDir, "micronaut-test-bom-module-1.2.3.toml")
        moduleDir.exists()
        catalogFile.exists()
        matchesRestrictedCatalog(catalogFile)

        and:
        def pomFile = new File(moduleDir, "micronaut-test-bom-module-1.2.3.pom")
        matchesTestBomModulePom(pomFile, false)
    }

    def "can inline a (non Micronaut) BOM"() {

        given:
        withSample("test-bom-external-inlining")
        buildFile << """
            micronautBom {
                inlineRegularBOMs = true
            }
        """

        when:
        run 'publishAllPublicationsToBuildRepository'

        then:
        def moduleDir = file("build/repo/io/micronaut/dummy/micronaut-test-bom-external-inlining/1.2.3")
        def catalogFile = new File(moduleDir, "micronaut-test-bom-external-inlining-1.2.3.toml")
        moduleDir.exists()
        catalogFile.exists()
        matchesExternalCatalog(catalogFile, false)

        and:
        def pomFile = new File(moduleDir, "micronaut-test-bom-external-inlining-1.2.3.pom")
        def mavenProperties = new XmlSlurper().parse(pomFile)
                .properties
                .childNodes()
                .collectEntries { node -> [node.name(), node.text()] }
        mavenProperties.get('junit.jupiter.api.version') == version("test-bom-external-inlining", "managed-junit")
        mavenProperties.get('junit.platform.suite.version') == junitPlatformVersion(version("test-bom-external-inlining", "managed-junit"))
    }

    @Unroll
    def "can infer a version from a transitive dependency"() {

        given:
        withSample("test-bom-external-inlining")
        buildFile << """
            micronautBom {
                inlineRegularBOMs = true
                $notation
            }
        """

        when:
        run 'publishAllPublicationsToBuildRepository'

        then:
        def moduleDir = file("build/repo/io/micronaut/dummy/micronaut-test-bom-external-inlining/1.2.3")
        def catalogFile = new File(moduleDir, "micronaut-test-bom-external-inlining-1.2.3.toml")
        moduleDir.exists()
        catalogFile.exists()
        matchesExternalCatalog(catalogFile, true)

        and:
        def pomFile = new File(moduleDir, "micronaut-test-bom-external-inlining-1.2.3.pom")
        def bom = new XmlSlurper().parse(pomFile)
        def mavenProperties = bom
                .properties
                .childNodes()
                .collectEntries { node -> [node.name(), node.text()] }
        mavenProperties.get('opentest4j.version') == '1.3.0'
        bom.dependencyManagement.dependencies.dependency.find {
            it.groupId[0].text()=='org.opentest4j' &&
                    it.artifactId[0].text() == 'opentest4j' &&
                    it.version[0].text() == '${opentest4j.version}'
        }

        where:
        notation << [
                "inferredManagedDependencies = [\n" +
                        "                    'org.opentest4j:opentest4j' : 'opentest4j'\n" +
                        "                ]",
                "inferredManagedDependencies(['org.opentest4j:opentest4j'])"
        ]
    }

    @Unroll
    def "can exclude an alias when inlining Micronaut catalogs into the generated catalog"() {
        given:
        withSample("test-bom-module")
        buildFile << """
        micronautBom {
            $notation
        }
        """
        when:
        run 'publishAllPublicationsToBuildRepository'

        then:
        def moduleDir = file("build/repo/io/micronaut/dummy/micronaut-test-bom-module/1.2.3")
        def catalogFile = new File(moduleDir, "micronaut-test-bom-module-1.2.3.toml")
        moduleDir.exists()
        catalogFile.exists()
        matchesTestBomModuleCatalog(catalogFile, false)

        and:
        def pomFile = new File(moduleDir, "micronaut-test-bom-module-1.2.3.pom")
        pomFile.exists()
        matchesTestBomModulePom(pomFile, false)

        where:
        notation << [
                'excludedInlinedAliases.add("micronaut-aws-common")',
                'excludeFromInlining("*", "micronaut-aws-common")',
                'excludeFromInlining("micronaut-aws-bom", "micronaut-aws-common")'
        ]
    }

    private boolean matchesTestBomModuleCatalog(File catalogFile, boolean includesCommon) {
        def text = catalogFile.text
        def sampleVersions = versions("test-bom-module")
        assert text.contains('format.version = "1.1"')
        assert text.contains('aws-lambda = "')
        assert text.contains('aws-lambda-events = "')
        assert text.contains('aws-lambda-java-serialization = "')
        assert text.contains('dekorate = "' + sampleVersions['managed-dekorate'] + '"')
        assert text.contains('junit = "' + sampleVersions['managed-junit'] + '"')
        assert text.contains('ksp = "' + sampleVersions['managed-ksp'] + '"')
        assert text.contains('micronaut-aws = "' + sampleVersions['managed-micronaut-aws'] + '"')
        assert text.contains('micronaut-test-bom-module = "1.2.3"')
        assert text.contains('micronaut-aws-bom = {group = "io.micronaut.aws", name = "micronaut-aws-bom", version.ref = "micronaut-aws" }')
        assert text.contains('micronaut-aws-common =') == includesCommon
        assert text.contains('hard-versioned = {id = "some.other.id", version = "1.2.3" }')
        assert text.contains('ksp = {id = "com.google.devtools.ksp", version.ref = "ksp" }')
        true
    }

    private boolean matchesRestrictedCatalog(File catalogFile) {
        def text = catalogFile.text
        def sampleVersions = versions("test-bom-module")
        assert text.contains('dekorate = "' + sampleVersions['managed-dekorate'] + '"')
        assert text.contains('junit = "' + sampleVersions['managed-junit'] + '"')
        assert text.contains('ksp = "' + sampleVersions['managed-ksp'] + '"')
        assert text.contains('micronaut-aws = "' + sampleVersions['managed-micronaut-aws'] + '"')
        assert text.contains('aws-lambda-core = {group = "com.amazonaws", name = "aws-lambda-java-core", version.ref = "aws-lambda" }')
        assert !text.contains('micronaut-aws-common =')
        assert !text.contains('alexa-ask-sdk = {group = "com.amazon.alexa"')
        true
    }

    private boolean matchesExternalCatalog(File catalogFile, boolean includesInferredDependency) {
        def text = catalogFile.text
        def junitVersion = version("test-bom-external-inlining", "managed-junit")
        def platformVersion = junitPlatformVersion(junitVersion)
        assert text.contains('junit = "' + junitVersion + '"')
        assert text.contains('junit-jupiter-api = "' + junitVersion + '"')
        assert text.contains('junit-platform-suite = "' + platformVersion + '"')
        assert text.contains('boms-junit = {group = "org.junit", name = "junit-bom", version.ref = "junit" }')
        assert text.contains('micronaut-test-bom-external-inlining = "1.2.3"')
        assert text.contains('opentest4j = "1.3.0"') == includesInferredDependency
        assert text.contains('opentest4j = {group = "org.opentest4j", name = "opentest4j", version.ref = "opentest4j" }') == includesInferredDependency
        true
    }

    private boolean matchesTestBomModulePom(File pomFile, boolean includesCommon) {
        def props = new XmlSlurper().parse(pomFile)
                .properties
                .childNodes()
                .collectEntries { node -> [node.name(), node.text()] }
        def sampleVersions = versions("test-bom-module")
        assert props['dekorate.version'] == sampleVersions['managed-dekorate']
        assert props['junit.version'] == sampleVersions['managed-junit']
        assert props['ksp.version'] == sampleVersions['managed-ksp']
        assert props['micronaut.aws.version'] == sampleVersions['managed-micronaut-aws']
        true
    }

    private Map<String, String> versions(String sample) {
        file("gradle/libs.versions.toml")
                .readLines()
                .collectEntries { line ->
                    def matcher = line =~ /^([A-Za-z0-9_.-]+)\s*=\s*"([^"]+)"$/
                    matcher.matches() ? [(matcher[0][1]): matcher[0][2]] : [:]
                }
    }

    private String version(String sample, String alias) {
        versions(sample)[alias]
    }

    private String junitPlatformVersion(String junitVersion) {
        junitVersion.startsWith("5.") ? "1." + junitVersion.substring(2) : junitVersion
    }

    @Issue("https://github.com/micronaut-projects/micronaut-build/issues/284")
    def "uses a single version for all subprojects"() {
        withSample("multi-project-bom")
        settingsFile.text += """rootProject.name = '$rootName' """

        when:
        run 'publishAllPublicationsToBuildRepository'

        then:
        def moduleDir = file("build/repo/io/micronaut/freedom/micronaut-freedom-bom/1.2.3")
        def catalogFile = new File(moduleDir, "micronaut-freedom-bom-1.2.3.toml")
        moduleDir.exists()
        catalogFile.exists()
        println catalogFile.text
        catalogFile.text.trim() == """#
# This file has been generated by Gradle and is intended to be consumed by Gradle
#
[metadata]
format.version = "1.1"

[versions]
micronaut-freedom = "1.2.3"

[libraries]
micronaut-freedom-bar = {group = "io.micronaut.freedom", name = "micronaut-freedom-bar", version.ref = "micronaut-freedom" }
micronaut-freedom-bom = {group = "io.micronaut.freedom", name = "micronaut-freedom-bom", version.ref = "micronaut-freedom" }
micronaut-freedom-foo = {group = "io.micronaut.freedom", name = "micronaut-freedom-foo", version.ref = "micronaut-freedom" }
""".trim()

        and:
        def pomFile = new File(moduleDir, "micronaut-freedom-bom-1.2.3.pom")
        pomFile.exists()
        println pomFile.text
        pomFile.text.contains """<properties>
    <micronaut.freedom.version>1.2.3</micronaut.freedom.version>
  </properties>"""
        pomFile.text.contains """<dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.micronaut.freedom</groupId>
        <artifactId>micronaut-freedom-bar</artifactId>
        <version>\${micronaut.freedom.version}</version>
      </dependency>
      <dependency>
        <groupId>io.micronaut.freedom</groupId>
        <artifactId>micronaut-freedom-foo</artifactId>
        <version>\${micronaut.freedom.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>"""

        where:
        rootName << ['freedom', 'freedom-parent']
    }

    @Issue("https://github.com/micronaut-projects/micronaut-build/issues/284")
    def "can explicitly define the main property name"() {
        withSample("multi-project-bom")
        settingsFile.text += """rootProject.name = '$rootName' """
        file("freedom-bom/build.gradle") << """
micronautBom {
   propertyName = 'democracy'
}
        """

        when:
        run 'publishAllPublicationsToBuildRepository'

        then:
        def moduleDir = file("build/repo/io/micronaut/freedom/micronaut-freedom-bom/1.2.3")
        def catalogFile = new File(moduleDir, "micronaut-freedom-bom-1.2.3.toml")
        moduleDir.exists()
        catalogFile.exists()
        println catalogFile.text
        catalogFile.text.trim() == """#
# This file has been generated by Gradle and is intended to be consumed by Gradle
#
[metadata]
format.version = "1.1"

[versions]
micronaut-democracy = "1.2.3"

[libraries]
micronaut-freedom-bar = {group = "io.micronaut.freedom", name = "micronaut-freedom-bar", version.ref = "micronaut-democracy" }
micronaut-freedom-bom = {group = "io.micronaut.freedom", name = "micronaut-freedom-bom", version.ref = "micronaut-democracy" }
micronaut-freedom-foo = {group = "io.micronaut.freedom", name = "micronaut-freedom-foo", version.ref = "micronaut-democracy" }
""".trim()

        and:
        def pomFile = new File(moduleDir, "micronaut-freedom-bom-1.2.3.pom")
        pomFile.exists()
        println pomFile.text
        pomFile.text.contains """<properties>
    <micronaut.democracy.version>1.2.3</micronaut.democracy.version>
  </properties>"""
        pomFile.text.contains """<dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.micronaut.freedom</groupId>
        <artifactId>micronaut-freedom-bar</artifactId>
        <version>\${micronaut.democracy.version}</version>
      </dependency>
      <dependency>
        <groupId>io.micronaut.freedom</groupId>
        <artifactId>micronaut-freedom-foo</artifactId>
        <version>\${micronaut.democracy.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>"""

        where:
        rootName << ['freedom', 'freedom-parent']
    }
}
