package io.micronaut.build

class MicronautBuildExtension {

    String sourceCompatibility = '1.8'
    String targetCompatibility = '1.8'

    String checkstyleVersion = '8.33'

    String dependencyUpdatesPattern = /.+(-|\.?)(b|M|RC)\d.*/

    Closure resolutionStrategy

    boolean enforcedPlatform = false

    void resolutionStrategy(Closure closure) {
        this.resolutionStrategy = closure
    }

}
