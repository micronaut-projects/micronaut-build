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

### Catalog version fallback

`micronautBuild.importMicronautCatalog("micronaut-reactor")` in settings imports
the catalog using its local library version, as before. If the library has no
version declaration, the import looks up its Maven coordinates in the catalog
published with the selected Micronaut platform BOM, then in the configured core
catalog if the platform does not supply a version:

```toml
[versions]
micronaut = "5.2.22"
micronaut-platform = "5.2.5"

[libraries]
micronaut-reactor = { module = "io.micronaut.reactor:micronaut-reactor-bom" }
```

Custom aliases still require a library declaration. Known Micronaut BOM names,
such as `micronaut-reactor`, can omit the entire declaration: the plugin uses the
known coordinates when the alias is absent locally. If neither catalog supplies that module's version,
the import fails with an error requesting an explicit local version. Existing
explicit versions, references, and rich-version handling are unchanged. This
fallback only selects the imported catalog version; it does not apply additional
BOM constraints to project dependencies. The platform catalog is resolved only
for versionless imports and can also be selected with the `micronautPlatformVersion` Gradle
property. Lookup matches Maven group and artifact, so platform aliases such as
`boms-micronaut-reactor` and version keys such as `managed-micronaut-reactor` work.
Keep the `micronaut` version for the standard no-argument `importMicronautCatalog()`;
this change does not infer the core version from the platform.

To also apply an imported BOM's dependency constraints, explicitly opt in:

```groovy
micronautBuild {
    importMicronautCatalog("micronaut-reactor", true)
}
```

The one-argument call and an explicit `false` remain catalog-only. The core BOM
continues to be applied by the common build conventions as before. With `true`,
the resolved module BOM is added alongside core through `globalBoms`, which feeds
`implementation`, `annotationProcessor`, and `testAnnotationProcessor`. Standard
Java test classpaths inherit the implementation constraints. Each project's
`micronautBuild.enableBom = false` disables included BOMs and the common plugin's
core management. Manually declared BOMs keep their existing behavior. Projects
with an existing application `micronautBoms` bucket and the maintainer build
extension receive the included BOMs there as well. Plain Java projects also receive
the maintainer extension and a `globalBoms` bucket when needed, covering implementation
and annotation processors. Catalog-only imports without a configured platform do not
add this wiring.
Opting in can change dependency resolution and published platform
dependencies; existing catalog-only imports keep their behavior.

When `[versions].micronaut-platform` or the `micronautPlatformVersion` Gradle
property is configured, its platform BOM is included automatically alongside core.
This supplies the platform's own constraints, such as Lombok, as well as its module
BOM imports. No platform library alias or explicit platform catalog import is
required. The selected version remains a bootstrap selector and must be kept.
The automatic platform respects each project's `micronautBuild.enableBom` setting
and uses `globalBoms` or the application `micronautBoms` bucket.

Additional module BOMs still require `importMicronautCatalog(alias, true)` to be
included separately; ordinary one-argument calls remain catalog-only. This permits
explicit module version overrides alongside the platform baseline. If a platform
catalog alias is imported explicitly with `true` at the same version, its BOM is
not added twice. A configured platform may reference the current project's
published BOM, so verify dependency graphs and publications when enabling it.

### Importing known Micronaut BOMs without local aliases

Use `MicronautBom` to import a known BOM without declaring that BOM in
`libs.versions.toml`:

```groovy
import io.micronaut.build.MicronautBom

micronautBuild {
    importMicronautCatalog(MicronautBom.REACTOR, true)
    importMicronautCatalog(MicronautBom.SQL, true)
    importMicronautCatalog(MicronautBom.SERDE)
}
```

These imports preserve catalog accessor names such as `mnReactor`, `mnSql`, and
`mnSerde`. The enum holds Maven coordinates only. A local library declaration
matching those coordinates takes priority, including explicit and rich versions.
Otherwise versions come from the configured platform catalog first, then core. Keep the
platform/core version selectors. The one-argument enum overload is catalog-only;
pass `true` to also apply that BOM, subject to `enableBom`.

The enum lists the 72 Micronaut BOMs in the
[published platform 5.2.6 catalog](https://repo.maven.apache.org/maven2/io/micronaut/platform/micronaut-platform/5.2.6/micronaut-platform-5.2.6.toml),
including `MONGO`, `SERDE`, and `TEST_RESOURCES`. It excludes third-party BOMs,
which generally do not publish Micronaut version catalogs. Availability depends
on the selected platform/core version: an absent BOM fails with a lookup error.
Use the existing string alias API for explicit local overrides or custom BOMs;
local declarations still take precedence, including rich versions. Existing
calls such as `importMicronautCatalog("micronaut-reactor", true)` therefore work
after deleting the corresponding BOM library declaration. The shorthand
`micronaut-testresources` also remains supported and creates `mnTestResources`.
The no-argument import continues to provide the core
`mn` catalog as before (`MicronautBom.CORE` follows the named-import convention,
creating `mnCore`).

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

### TypeScript snippets

The `snippet::` macro looks up TypeScript snippets in `test-suite-typescript/src/test/typescript`, by the whole
package: `snippet::io.micronaut.docs.Foo` resolves to `test-suite-typescript/src/test/typescript/io/micronaut/docs/Foo.ts`.
A test written against a TypeScript test runner declares no class and is named by the convention of the runners, so
where there is no `Foo.ts` the snippet is the test file of that name, `Foo.test.ts` or `Foo.spec.ts`. The `project`,
`project-base`, `source` and `source-typescript` attributes apply as for the other languages, and the tags of a
snippet are `// tag::name[]` comments as in Java.

A module that has a project for a language logs a warning for every snippet without a file in that language. While
the snippets of a language are still being written, name the language in the `snippet-optional-languages` document
attribute, a list separated by commas, to leave those warnings out:

```groovy
tasks.withType(PublishGuideTask).configureEach {
    properties.put("snippet-optional-languages", "typescript")
}
```

TypeScript snippets are not highlighted yet: the bundled highlight.js has no grammar for the language.

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

### Lambda desugaring (trial)

The `micronautBuild.desugarLambdas` Gradle property switches on a trial of build-time lambda desugaring
([#956](https://github.com/micronaut-projects/micronaut-build/issues/956)) in modules that apply
`io.micronaut.build.internal.base-module`: `true` for every module, or a comma-separated list of project names,
with or without their `micronaut-` prefix (for example `-PmicronautBuild.desugarLambdas=core,inject`). A
`desugarLambdas` task writes a copy of `compileJava`'s output in which lambda and method-reference call sites call
classes generated at build time, the `jar` task packs that copy instead of `compileJava`'s output, and the sites
rewritten and kept are reported in `build/reports/desugarLambdas/main.txt`. Tests, javadoc, the sources jar and
the IDE keep the compiler's output. Without the property the build is unchanged.

Also, to pin a dependency to a particular version:

```groovy
micronautBuild {
    resolutionStrategy {
        force "com.rabbitmq:amqp-client:${rabbitVersion}"
    }    
}
```

You can use [the same DSL as in Gradle](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html).
