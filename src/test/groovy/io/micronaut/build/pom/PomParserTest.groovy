package io.micronaut.build.pom

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Path


class PomParserTest extends Specification {
    @TempDir
    private Path tmpDir

    private PomDownloader downloader
    private PomFile pom
    private Set<String> downloaded = []

    @Subject
    PomParser parser

    def setup() {
        downloader = new PomDownloader(
                ["https://repo1.maven.org/maven2"],
                tmpDir.toFile()
        ) {
            @Override
            Optional<File> tryDownloadPom(PomDependency dependency) {
                def maybe =  super.tryDownloadPom(dependency)
                if (maybe.isPresent()) {
                    downloaded.add("${dependency.groupId}:${dependency.artifactId}:${dependency.version}".toString())
                }
                maybe
            }
        }
        parser = new PomParser(downloader)
    }

    def "properly resolves AWS BOM parent property"() {
        when:
        parse("software.amazon.awssdk", "bom", "2.17.134")

        then:
        pom.properties['awsjavasdk.version'] == '2.17.134'
        downloaded == ['software.amazon.awssdk:bom:2.17.134', 'software.amazon.awssdk:aws-sdk-java-pom:2.17.134'] as Set
        pom.dependencies.every { it.version == '2.17.134'}
    }


    private void parse(String group, String artifact, String version) {
        def maybePom = downloader.tryDownloadPom(new PomDependency(false, group, artifact, version, "compile"))
        def pomFile = maybePom.get()
        pom = parser.parse(pomFile, group, artifact, version)
    }

}
