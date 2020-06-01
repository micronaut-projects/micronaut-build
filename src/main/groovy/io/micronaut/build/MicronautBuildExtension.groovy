package io.micronaut.build

import org.gradle.api.Action

class MicronautBuildExtension {

    String sourceCompatibility = '1.8'
    String targetCompatibility = '1.8'

    String checkstyleVersion = '8.32'

    String dependencyUpdatesPattern = /.+(-|\.?)(b|M|RC)\d.*/

    List<String> forceDependencies = []

    void requiredDependencyVersion(String id) {
        if (id) {
            forceDependencies.add(id)
        }
    }
}
