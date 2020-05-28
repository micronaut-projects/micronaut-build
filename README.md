# micronaut-build

Micronaut internal Gradle plugins. Not intended to be used in user's projects.

## Usage

The plugins are published in Bintray:

```groovy
buildscript {
    repositories {
        maven { url  "https://dl.bintray.com/micronaut/core-releases-local" }
    }
    dependencies {
        classpath "io.micronaut.build:micronaut-gradle-plugins:2.0.0.RC2"
    }
}
```

Then, apply the individual plugins as desired

## Available plugins

* `io.micronaut.build.common`.
    * Defines `jcenter()` as a project repository.
    * Configures the version to the `projectVersion` property (usually defined in `gradle.properties`).
    * Configures Java / Groovy compilation options.
    * Configures dependencies, enforcing the Micronaut BOM defined in `micronautVersion` property, as well as the versions
      defined in `groovyVersion`, `spockVersion` and `micronautTestVersion`.
    * Configures the IDEA plugin.
    * Configures Checkstyle.
    * Configures the Spotless plugin, to apply license headers.
    * Configures the test logger plugin.
    
* `io.micronaut.build.dependency-updates`:
    * Configures the `com.github.ben-manes.versions` plugin to check for outdated dependencies.
    
* `io.micronaut.build.publishing`:
    * Configures publishing to JFrog OSS, Bintray and Maven Central.
    
## Configuration options

Default values are:

```groovy
micronautBuild {
    sourceCompatibility = '1.8'
    targetCompatibility = '1.8'

    checkstyleVersion = '8.32'

    dependencyUpdatesPattern = /.+(-|\.?)(b|M|RC)\d.*/
}
```

Also, to pin a dependency to a particular version:

```groovy
configurations {
    all {
        resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            String group = details.requested.group
            if(group == 'com.rabbitmq') {
                details.useVersion(rabbitVersion)
            }
        }
    }
}
```
