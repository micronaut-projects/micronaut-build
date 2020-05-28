package io.micronaut.build

class MicronautBuildExtension {

    String sourceCompatibility = '1.8'
    String targetCompatibility = '1.8'

    String checkstyleVersion = '8.32'

    String dependencyUpdatesPattern = /.+(-|\.?)(b|M|RC)\d.*/
}
