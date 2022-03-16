package io.micronaut.build.pom

import groovy.transform.PackageScope
import groovy.xml.XmlSlurper

@PackageScope
class PomParser {
    private final PomDownloader pomDownloader

    PomParser(PomDownloader pomDownloader) {
        this.pomDownloader = pomDownloader
    }

    PomFile parse(File pomFile, String groupId, String artifactId, String version) {
        def pom = new XmlSlurper().parse(pomFile)
        def bom = pom.packaging.text() == 'pom'
        Map<String, String> properties = pom.properties.children().collectEntries {
            [it.name(), it.text()]
        }
        if (!properties.containsKey("project.version")) {
            properties['project.version'] = version
        }
        def parentGroupId = pom.parent.groupId.text()
        def parentArtifactId = pom.parent.artifactId.text()
        def parentVersion = pom.parent.version.text()
        if (parentGroupId && parentArtifactId && parentVersion) {
            Optional<File> parentPom = pomDownloader.tryDownloadPom(new PomDependency(
                    false,
                    parentGroupId,
                    parentArtifactId,
                    parentVersion,
                    ""
            ))
            if (parentPom.present) {
                PomFile parent = parse(parentPom.get(), parentGroupId, parentArtifactId, parentVersion)
                parent.properties.each { k, v ->
                    if (!properties.containsKey(k)) {
                        properties.put(k, v)
                    }
                }
            }
        }
        def managedDependencies = pom.dependencyManagement.dependencies.dependency.collect {
            parseDependency(it, groupId, true, properties)
        }
        def dependencies = pom.dependencies.dependency.collect {
            parseDependency(it, groupId, false, properties)
        }
        return new PomFile(groupId, artifactId, version, bom, dependencies + managedDependencies, properties)
    }

    static PomDependency parseDependency(Object model, String group, boolean managed, Map<String, String> properties) {
        String depGroup = model.groupId.text().replace('${project.groupId}', group)
        String depArtifact = model.artifactId.text()
        String depVersion = model.version.text()
        boolean substituteProperties = true
        while (substituteProperties) {
            substituteProperties = false
            for (Map.Entry<String, String> property : properties) {
                if (depVersion == "\${${property.key}}" || depVersion == "\$${property.key}") {
                    depVersion = property.value
                    substituteProperties = true // need to recurse because some properties can reference others
                    break
                }
            }
        }
        String depScope = model.scope.text()
        new PomDependency(managed, depGroup, depArtifact, depVersion, depScope)
    }
}
