package io.micronaut.build.pom

import groovy.json.JsonSlurper

class PomFileAdapter {
    static PomValidation parseFromFile(File path) {
        def json = new JsonSlurper().parse(path)
        def pomFile = new PomFile(
                json.pomFile.groupId, json.pomFile.artifactId, json.pomFile.version,
                json.pomFile.bom,
                json.pomFile.dependencies.collect { toDependency(it) },
                json.pomFile.properties
        )
        def dependencyPath = json.dependencyPath
        def validation = new PomValidation(dependencyPath, pomFile, json.validDependencies, json.invalidDependencies as Set)
        return validation
    }

    static PomDependency toDependency(Object json) {
        new PomDependency(
                json.managed,
                json.groupId,
                json.artifactId,
                json.version,
                json.scope
        )
    }
}
