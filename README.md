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
* `io.micronaut.build.internal.python`
    * Adds Python (Pyronaut) compilation support: every source set gets a `src/<sourceSet>/python` source directory
      compiled by a `compile<SourceSet>Python` task (`compilePython` for `main`) into the source set output.
      See [Python support](#python-support).
* `io.micronaut.build.internal.quality-checks`
    * Applied automatically by the `common` plugin; configures Checkstyle, Jacoco and Sonar, and registers the
      `sonarLint` task on Java projects. See [Offline Sonar check](#offline-sonar-check).
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

### Python support

The `io.micronaut.build.internal.python` plugin compiles Python sources with the Pyronaut compiler shipped with
Micronaut core (`io.micronaut:micronaut-inject-python` and `io.micronaut:micronaut-context-python`). It is typically
applied to a `test-suite-python` project so that the user guide can include Python snippets next to the Java, Kotlin
and Groovy ones:

```groovy
plugins {
    id("io.micronaut.build.internal.convention-test-library")
    id("io.micronaut.build.internal.python")
}
```

The compiler dependencies are added to the `pyronautCompiler` configuration using the `micronaut` version of the
version catalog (or the `micronautVersion` property). The version can be changed, or set to an empty string to
declare the `pyronautCompiler` dependencies manually, which is what Micronaut core does since it builds the compiler:

```groovy
micronautBuild {
    python {
        compilerVersion = "" // declare the compiler dependencies manually
    }
}

dependencies {
    pyronautCompiler(projects.micronautInjectPython)
    pyronautCompiler(projects.micronautContextPython)
}
```

Python tests need a GraalVM runtime and are slow, so the `Test` tasks of a project applying the plugin only run
when the `python-ci` Gradle property is set. The dedicated "Python CI" GitHub workflow of the project template runs
`./gradlew pythonCheck -Ppython-ci` on GraalVM: `pythonCheck` is a root project task aggregating the `check` task
of every project applying the plugin (a build can register `pythonCheck` in its root project itself to add other
projects to it). The regular CI still compiles the Python sources. The test convention can be changed with:

```groovy
micronautBuild {
    python {
        testsEnabled = true
    }
}
```

A large Python test suite can be split across several CI jobs: the workflow runs one job per shard with
`./gradlew pythonCheck -Ppython-ci -Ppython-ci-shard=<index>/<count>` (a 1-based index, for example `2/4` for the
second of four jobs), and every `Test` task then only runs the test classes assigned to that shard. Classes are
assigned by the hash of their top level class name, so nested classes run with their declaring class and every job
computes the same partition. The `micronautBuild.python.testShard` property overrides the Gradle property, and a
build which adds projects without Python sources to `pythonCheck` itself shards their tests with
`MicronautPythonPlugin.shardTests(project)`.

Annotation processor options (the `-Akey=value` arguments `JavaCompile` takes through `options.compilerArgs`) can
be passed to the Python compiler for every compile task of the project, or per task. They are appended after the
options the plugin sets itself, and reach type element visitors through `VisitorContext.getOptions()`:

```groovy
micronautBuild {
    python {
        compilerArgs.add("-Amicronaut.jsonschema.baseUri=https://example.com/schemas")
    }
}

tasks.named("compileTestPython") {
    compilerArgs.add("-Amicronaut.openapi.project.dir=${projectDir}")
}
```

Python sources are compiled after the Java classes of the source set, so a test suite whose Python sources use
Java classes of the same project should declare:

```groovy
tasks.named("compileTestPython") {
    dependsOn(tasks.named("classes"))
    classpath.from(sourceSets.main.output)
}
```

The documentation `snippet::` macro looks up Python snippets in `test-suite-python/src/test/python`, with the
`io/` prefix of the package removed (`snippet::io.micronaut.docs.Foo` resolves to
`test-suite-python/src/test/python/micronaut/docs/Foo.py`), and the `dependency:` macro renders a `pyproject.toml`
snippet for Pyronaut next to the Gradle and Maven ones (see the `BuildDependencyMacro` documentation for the
`pyronautScope` and `pyronaut` attributes).

## Offline Sonar check

Every Java project gets a `sonarLint` task: it runs SonarSource's Java analyzer (the `sonar-java` plugin build
SonarCloud runs, with its symbolic execution plugin) locally, offline, with the rules of the SonarCloud
"Micronaut Profile". No server, token or Docker is needed.

```
./gradlew :micronaut-router:sonarLint -PsonarLint.baseRef=origin/5.3.x
```

By default it analyses only the Java files changed in the working tree (committed, uncommitted and untracked)
since the merge base of `HEAD` and the base ref, and reports only the issues on changed lines. The main and test
sources are analysed, with the compiled classes and the compile classpath of each source set, so it compiles the
project first.

Each issue is printed on its own line, followed by a summary line:

```
sonarlint: router/src/main/java/io/micronaut/web/router/RouteConditionContext.java:105:13: MAJOR BUG java:S2583 Change this condition so that it does not always evaluate to "false"
sonarlint: 2 gate-failing issues (BUG 2), 128 other issues on changed lines in 132 analysed files
```

The format is `sonarlint: <file>:<line>:<column>: <SEVERITY> <TYPE> <rule> <message>`, with the path relative to
the root project directory. The task fails when a gate-failing issue is reported: an issue of type `BUG` or
`VULNERABILITY`, or of severity `BLOCKER` or `CRITICAL`, which are the issue conditions of the Micronaut Quality
Gate. The failure message lists the gate-failing issues. The reports are written to
`build/reports/sonarlint/sonarlint.json` (the issues, with `file`, `line`, `column`, `endLine`, `endColumn`,
`rule`, `type`, `severity`, `message` and `gateFailing`, and a `summary`) and `build/reports/sonarlint/sonarlint.sarif`.

Gradle properties:

* `-PsonarLint.baseRef=<ref>`: the base ref. Defaults to `micronautBuild.sonarLint.baseRef`, else `origin/HEAD`
  (the default branch of the remote).
* `-PsonarLint.all`: analyses every file and reports every issue.

Configuration, with the defaults:

```groovy
micronautBuild {
    sonarLint {
        enabled = false               // whether `check` depends on `sonarLint`
        baseRef = 'origin/5.3.x'      // no default: -PsonarLint.baseRef, else origin/HEAD
        allFiles = false
        includeTests = true
        failOnIssues = true
        showAllIssues = true          // false prints the gate-failing issues only
        gateTypes = ['BUG', 'VULNERABILITY']
        gateSeverities = ['BLOCKER', 'CRITICAL']
        rulesFile = rootProject.file('config/sonarlint/rules.json') // defaults to the bundled Micronaut Profile
        skippedRules = ['java:S1452': '...']
        excludes = ['**/tck/**']      // globs relative to the root project directory, like sonar.exclusions
    }
}
```

`check` does not depend on `sonarLint` by default: the analysis takes seconds to minutes per project, needs the
base branch fetched (CI checkouts are often shallow), and SonarCloud stays the reference on CI. Set `enabled = true`
to add it to `check`.

The task is cacheable: its inputs are the sources, the classpaths, the rules, the settings and the changed lines.
The analysis runs in a Gradle worker with an isolated class loader, so the engine's dependencies do not leak into
the build. The versions can be overridden in the version catalog: `sonarlint-engine`, `sonar-java`,
`sonar-java-symbolic-execution`.

### Rules

The plugin bundles the 490 active rules of the SonarCloud quality profile `AYFHlJjqlBO-fp5MP_nD` ("Micronaut
Profile", organization `micronaut-projects`), regenerated with `scripts/update-sonarlint-rules.py`. A project can
export another profile with the `sonarLintExportRules` task of the root project, and point `rulesFile` to it:

```
./gradlew sonarLintExportRules -PsonarLint.organization=micronaut-projects -PsonarLint.qualityProfile=<key>
```

It writes `config/sonarlint/rules.json` through the public SonarCloud API.

`java:S1452` is skipped by default: SonarCloud never raises it on Micronaut projects, while the local analyzer raises
it on every `Foo<?>` return type, including unchanged code.

Limits:

* 28 rules of the profile need commercial analyzers and are not run locally: the 22 `javasecurity` taint analysis
  rules and the 6 `javabugs` rules.
* No coverage or duplication checks: only the issue conditions of the quality gate.

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

By default, the build uses Gradle's source and target compatibility settings so
projects continue to work with only the current JDK installed. Gradle
Toolchains remain opt-in: set `USE_GRADLE_TOOLCHAINS` to an empty value or
`true` to use `micronautBuild.javaVersion` for compilation toolchains and
`micronautBuild.testJavaVersion` for `Test` task launchers. Set
`USE_GRADLE_TOOLCHAINS=false` or leave it unset to keep the default single-JDK
behavior.

Also, to pin a dependency to a particular version:

```groovy
micronautBuild {
    resolutionStrategy {
        force "com.rabbitmq:amqp-client:${rabbitVersion}"
    }    
}
```

You can use [the same DSL as in Gradle](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html).
