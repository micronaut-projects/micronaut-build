package io.micronaut.build.compat

import spock.lang.Specification

class MavenMetadataVersionParserTest extends Specification {
    def "tests parsing of maven metadata"() {
        def metadata = MavenMetadataVersionParserTest.getResourceAsStream("/test-maven-metadata.xml").bytes

        when:
        def versions = MavenMetadataVersionHelper.findReleasesFrom(metadata)

        then:
        versions.size() == 125
        versions[0].version == "1.0.0"
        versions[124].version == "3.8.6"

        previousReleaseOf("3.8.7", versions) == "3.8.6"
        previousReleaseOf("1.1.0", versions) == "1.0.5"
        previousReleaseOf("4.0.0", versions) == "3.8.6"
        previousReleaseOf("0.5.0", versions) == null
        previousReleaseOf("1.0.5", versions) == "1.0.4"
    }

    static String previousReleaseOf(String version, List<VersionModel> versions) {
        MavenMetadataVersionHelper.findPreviousReleaseFor(VersionModel.of(version), versions).orElse(null)
    }
}
