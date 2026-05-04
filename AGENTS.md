# Micronaut Build Agent Guidance

Micronaut Build publishes internal Gradle plugins used by Micronaut
repositories. Treat changes here as build-infrastructure changes: small
behavior shifts can affect downstream builds, releases, documentation
generation, and publishing.

## Repository Layout

- `buildSrc/src/main/kotlin/` contains the repository's own convention plugins.
  The root build applies `io.micronaut.build.internal.parent`; plugin projects
  apply `io.micronaut.build.internal.gradle-plugin`.
- `micronaut-gradle-plugins/` contains the main internal plugin suite,
  including common build conventions, BOM/version-catalog support, publishing,
  quality, binary compatibility, docs, AOT, Develocity, and settings plugins.
- `micronaut-kotlin-build-plugins/` contains Kotlin, Kapt, and KSP companion
  plugins and depends on `micronaut-gradle-plugins`.
- `gradle/libs.versions.toml` is the dependency and plugin version catalog used
  by the build and by generated default-version classes.
- Functional test fixtures live under
  `micronaut-gradle-plugins/src/functionalTest/gradle-projects/`.
- Documentation engine code and shared guide assets live under
  `micronaut-gradle-plugins/src/main/groovy/io/micronaut/docs/` and
  `micronaut-gradle-plugins/src/main/template/`.

## Build And Version Ownership

- Keep repository build logic in `buildSrc` and plugin behavior in the owning
  plugin module. Do not duplicate build conventions across fixture projects.
- Add or change published plugin IDs through the `micronautBuildPlugin` blocks
  in the plugin project build files.
- When changing dependency versions, update `gradle/libs.versions.toml` and any
  corresponding `versionsMap` entries that generate default versions for
  consumers.
- `gradle.properties` owns `projectVersion` for the repository. Do not hard-code
  release versions in build scripts or tests unless a fixture explicitly needs a
  pinned value.
- BOM and version-catalog behavior is high impact. Prefer focused unit tests
  under `src/test/groovy/io/micronaut/build/catalogs/` or functional fixtures
  under `src/functionalTest/gradle-projects/` for changes in that area.

## Publishing And Release-Sensitive Areas

- Publishing, signing, and Sonatype wiring is centralized in
  `buildSrc/src/main/kotlin/io.micronaut.build.internal.parent.gradle.kts`,
  `buildSrc/src/main/kotlin/io.micronaut.build.internal.gradle-plugin.gradle.kts`,
  `.github/workflows/gradle.yml`, and `.github/workflows/release.yml`.
- Be careful with `GPG_*`, `SONATYPE_*`, `GRADLE_ENTERPRISE_*`, and GitHub token
  handling. Keep secrets in CI/environment plumbing, never in source.
- Signing is skipped for `-SNAPSHOT` versions and when `skipSigning` is present.
  Preserve that behavior unless the release process is intentionally changing.
- Release workflows publish artifacts on push/release events. Treat workflow,
  artifact ID, group ID, POM metadata, and staging repository changes as
  release-impacting.

## Docs And Assets

- This repository does not have a normal root `src/main/docs/guide` user guide.
  It provides the docs plugin implementation and reusable guide templates for
  downstream Micronaut projects.
- Keep Asciidoc macro behavior in `io.micronaut.docs.*` covered by tests under
  `micronaut-gradle-plugins/src/test/groovy/io/micronaut/docs/`.
- Keep documentation publishing tasks and resource preparation behavior in
  `io.micronaut.build.docs.*` covered by unit or functional tests.
- Shared CSS, JavaScript, fonts, images, and layout templates in
  `src/main/template/` are packaged into `grails-doc-files.jar`; avoid cosmetic
  churn unless the generated documentation output is intentionally changing.
- There is no repository-level release-notes tree in this checkout. Release
  dropdown behavior is tested with
  `micronaut-gradle-plugins/src/test/resources/io.micronaut.build.utils/releases.json`.

## Verification

- For documentation-only or guidance-only changes, run:
  `git diff --check`.
- For build logic or plugin behavior changes, run the narrowest relevant test
  first, for example:
  `./gradlew :micronaut-gradle-plugins:test`,
  `./gradlew :micronaut-gradle-plugins:functionalTest`, or
  `./gradlew :micronaut-kotlin-build-plugins:test`.
- Before handing off behavior changes that can affect downstream projects, run:
  `./gradlew check`.
- CI uses Java 25. Match that locally when reproducing CI failures.

## Repo-Specific Anti-Patterns

- Do not add network work during Gradle configuration. Keep download/API calls in
  tasks or services with explicit inputs and predictable execution.
- Do not bypass existing helpers for GitHub, version parsing, downloading,
  dependency resolution, POM checking, or generated docs behavior.
- Do not make fixture projects depend on local machine state beyond declared
  environment variables used by the functional test harness.
- Do not change publishing coordinates, plugin IDs, generated default versions,
  or docs template assets without targeted tests and a release-impact note.
- Do not mutate company-level agent packages for repository-local guidance.
