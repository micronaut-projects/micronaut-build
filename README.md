# micronaut-build [![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.build.internal/micronaut-gradle-plugins.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.micronaut.build.internal/micronaut-gradle-plugins)

Micronaut internal Gradle plugins. Not intended to be used in user's projects.

## Usage

The plugins are published in Maven Central:

```groovy
buildscript {
    dependencies {
        classpath "io.micronaut.build:micronaut-gradle-plugins:3.0.0"
    }
}
```

Then, apply the individual plugins as desired

## Available plugins

* `io.micronaut.build.internal.common`.
    * Defines `jcenter()` as a project repository.
    * Configures the version to the `projectVersion` property (usually defined in `gradle.properties`).
    * Configures Java / Groovy compilation options.
    * Configures dependencies, enforcing the Micronaut BOM defined in `micronautVersion` property, as well as the version
      defined in `groovyVersion`.
    * Configures the IDEA plugin.
    * Configures Checkstyle.
    * Configures the Spotless plugin, to apply license headers.
    * Configures the test logger plugin.
    
* `io.micronaut.build.internal.dependency-updates`:
    * Configures the `com.github.ben-manes.versions` plugin to check for outdated dependencies.
    
* `io.micronaut.build.internal.publishing`:
    * Configures publishing to Sonatype OSSRH and Maven Central.

* `io.micronaut.build.internal.docs`:
    * Configures the guide publishing stuff.
    
## Configuration options

Default values are:

```groovy
micronautBuild {
    sourceCompatibility = '1.8'
    targetCompatibility = '1.8'

    checkstyleVersion = '8.33'

    dependencyUpdatesPattern = /.+(-|\.?)(b|M|RC)\d.*/
}
```

Also, to pin a dependency to a particular version:

```groovy
micronautBuild {
    resolutionStrategy {
        force "com.rabbitmq:amqp-client:${rabbitVersion}"
    }    
}
```

You can use [the same DSL as in Gradle](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html).
