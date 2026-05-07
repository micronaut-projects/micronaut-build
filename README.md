# micronaut-build [![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.build.internal/micronaut-gradle-plugins.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.micronaut.build.internal/micronaut-gradle-plugins)

Micronaut internal Gradle plugins. Not intended to be used in user's projects.

## Usage

The plugins are published in Maven Central:

```groovy
buildscript {
    dependencies {
        classpath "io.micronaut.build.internal:micronaut-gradle-plugins:<version>"
    }
}
```

Kotlin-specific build plugins are published separately:

```groovy
buildscript {
    dependencies {
        classpath "io.micronaut.build.internal:micronaut-kotlin-build-plugins:<version>"
    }
}
```

Then apply the individual plugins as needed.

## Available plugins

### Core plugins

* `io.micronaut.build.internal.common`
    * Configures the version to the `projectVersion` property (usually defined in `gradle.properties`).
    * Configures Java / Groovy compilation options.
    * Configures dependencies, enforcing the Micronaut BOM defined in `micronautVersion` property, as well as the version
      defined in `groovyVersion`.
    * Configures the IDEA plugin.
    * Configures Checkstyle.
    * Configures the Spotless plugin, to apply license headers.
    * Configures the test logger plugin.
* `io.micronaut.build.internal.aot-module`
    * Configures a Micronaut AOT module project.
* `io.micronaut.build.internal.base`
    * Applies the common Micronaut build extension.
* `io.micronaut.build.internal.base-module`
    * Configures a base Micronaut module project.
* `io.micronaut.build.internal.binary-compatibility-check`
    * Configures binary compatibility checks for published APIs.
* `io.micronaut.build.internal.bom`
    * Configures a Micronaut BOM project.
* `io.micronaut.build.internal.dependency-updates`
    * Configures the `com.github.ben-manes.versions` plugin to check for outdated dependencies.
* `io.micronaut.build.internal.develocity`
    * Configures Develocity build scan and build cache integration.
* `io.micronaut.build.internal.docs`
    * Configures Micronaut user guide, configuration reference, API documentation, and documentation archive tasks.
* `io.micronaut.build.internal.java-base`
    * Configures Java compilation defaults for Micronaut projects.
* `io.micronaut.build.internal.kotlin-base`
    * Configures Kotlin compilation defaults for Micronaut projects.
* `io.micronaut.build.internal.module`
    * Configures a standard Micronaut module project.
* `io.micronaut.build.internal.parent`
    * Configures root-project conventions for Micronaut builds.
* `io.micronaut.build.internal.parent-publishing`
    * Configures root-project publishing conventions.
* `io.micronaut.build.internal.publishing`
    * Configures publishing to Sonatype OSSRH and Maven Central.
* `io.micronaut.build.internal.quality-checks`
    * Applied automatically by the `common` plugin; configures Checkstyle, Jacoco and Sonar.
* `io.micronaut.build.internal.quality-reporting`
    * To be applied to the root project only; it consumes and aggregates the reports produced by the `quality-checks` plugin. 
* `io.micronaut.build.internal.version-catalog-updates`
    * Configures dependency update checks for projects that use Gradle version catalogs.
* `io.micronaut.build.shared.settings`
    * Configures shared settings conventions for Micronaut builds.

### Kotlin plugins

* `io.micronaut.build.internal.kotlin`
    * Configures Kotlin support for Micronaut projects.
* `io.micronaut.build.internal.kotlin-kapt`
    * Configures Kotlin annotation processing with KAPT.
* `io.micronaut.build.internal.kotlin-ksp`
    * Configures Kotlin symbol processing with KSP.

## Configuration options

Default values are:

```groovy
micronautBuild {
    javaVersion = 25
    testJavaVersion = JavaVersion.current().majorVersion as Integer

    checkstyleVersion = '12.1.0'

    dependencyUpdatesPattern = /(?i).+(-|\.?)(b|M|RC|Dev)\d?.*/

    enforcedPlatform = false
    enableProcessing = false
    enableBom = true
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
