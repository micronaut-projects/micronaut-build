# Micronaut Build Agent Guidance

Micronaut Build publishes internal Gradle plugins used by Micronaut
repositories. Treat changes here as build-infrastructure changes: small
behavior shifts can affect downstream builds, releases, documentation
generation, and publishing.

## Repository Layout

- `build.gradle` defines the internal Gradle plugin IDs and their
  implementation classes through the `gradlePlugin` block.
- `src/main/groovy/io/micronaut/build/` contains the main build, publishing,
  dependency update, quality, binary compatibility, AOT, and settings plugins.
- `src/main/groovy/io/micronaut/docs/` contains the documentation engine,
  Asciidoc macros, filters, dropdown helpers, and publishing support.
- `src/main/template/` contains shared guide templates, CSS, JavaScript, fonts,
  images, and layout assets that are packaged into `grails-doc-files.jar`.
- `gradle/libs.versions.toml` is the dependency and plugin version catalog used
  by the build and generated default-version classes.
- Functional test fixtures live under `src/functionalTest/gradle-projects/`.

## Build And Version Ownership

- Keep plugin behavior in the owning `src/main/groovy/io/micronaut/build/` or
  `src/main/groovy/io/micronaut/docs/` type. Do not duplicate build conventions
  across fixture projects.
- Add or change published plugin IDs only through the root `gradlePlugin` block.
- When changing dependency versions, update `gradle/libs.versions.toml` and any
  corresponding entries used by `generateVersions` in `build.gradle`.
- `gradle.properties` owns `projectVersion` for the repository. Do not hard-code
  release versions in build scripts or tests unless a fixture explicitly needs a
  pinned value.
- BOM, version-catalog, publishing, and binary-compatibility behavior is high
  impact. Prefer focused unit tests under `src/test/groovy/` or functional
  fixtures under `src/functionalTest/gradle-projects/` for changes in those
  areas.

## Publishing And Release-Sensitive Areas

- Publishing, signing, Sonatype, and POM metadata are configured in
  `build.gradle` and `.github/workflows/release.yml`.
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
  `src/test/groovy/io/micronaut/docs/`.
- Keep documentation publishing tasks and resource preparation behavior covered
  by unit or functional tests.
- Shared CSS, JavaScript, fonts, images, and layout templates in
  `src/main/template/` are packaged into `grails-doc-files.jar`; avoid cosmetic
  churn unless the generated documentation output is intentionally changing.
- There is no repository-level release-notes tree in this checkout. Release
  dropdown behavior is tested with
  `src/test/resources/io.micronaut.build.utils/releases.json`.

## Verification

- For documentation-only or guidance-only changes, run `git diff --check`.
- For build logic or plugin behavior changes, run the narrowest relevant test
  first, for example `./gradlew test` or `./gradlew functionalTest`.
- Before handing off behavior changes that can affect downstream projects, run
  `./gradlew check`.
- CI uses the Java version configured for the target branch. Match it locally
  when reproducing CI failures.

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
