plugins {
    id("io.micronaut.build.internal.gradle-plugin")
    id("groovy-gradle-plugin")
}

dependencies {
    constraints {
        implementation(libs.bundles.bouncycastle) {
            because("Use latest Bouncycastle to avoid ClassNotFoundExceptions when running tests")
        }
    }
    implementation(libs.commons.lang3)
    implementation(libs.commons.text)
    implementation(libs.snakeyaml)
    implementation(libs.grails.gdoc)
    implementation(libs.asciidoctorj)
    implementation(libs.jsoup)
    implementation(libs.spotless.plugin)
    implementation(libs.testlogger.plugin)
    implementation(libs.nexus.publish.plugin)
    implementation(libs.sonar.plugin)
    implementation(libs.graalvm.native.gradle.plugin)

    implementation(libs.develocity.plugin)
    implementation(libs.gradle.github.actions.plugin)
    implementation(libs.gradle.custom.userdata.plugin)
    implementation(libs.japicmp.plugin)
    implementation(libs.includegit.plugin)
    implementation(libs.sonatype.scan.plugin) {
        exclude(group = "org.codehaus.groovy")
    }

    implementation(libs.tomlj)
    implementation(libs.maven.model.builder)

    // We must differentiate the version that we use HERE to test the build plugins, which
    // should use a version of Spock which is compatible with what Gradle uses (Groovy 4)
    // and the version that we will use in Micronaut projects, which is going to be Groovy 5
    var localSpockVersion = libs.versions.spock.get().replace("groovy-5", "groovy-4")

    testImplementation(platform(libs.spock.bom)) {
        version {
            require(localSpockVersion)
        }
    }
    testImplementation(libs.spock.core) {
        version {
            require(localSpockVersion)
        }
    }

    testImplementation(libs.typesafe.config)

    testImplementation(libs.mockserver.server)
    testImplementation(libs.mockserver.client)
}

val docFilesJar = tasks.register<Jar>("docFilesJar") {
    description = "Package up files used for generating documentation."
    archiveVersion = null
    archiveFileName = "grails-doc-files.jar"
    from("src/main/template")
}

tasks.named<Jar>("jar") {
    from(docFilesJar)
}

micronautBuildPlugin {
    versionsFullyQualifiedClassName = "io.micronaut.build.utils.DefaultVersions"

    versionsMap.put("bytebuddy", libs.versions.bytebuddy)
    versionsMap.put("objenesis", libs.versions.objenesis)
    versionsMap.put("micronaut_build", version.toString())
    versionsMap.put("micronaut_docs", libs.versions.micronaut.docs)
    versionsMap.put("micronaut_logging", libs.versions.micronaut.logging)
    versionsMap.put("micronaut_test", libs.versions.micronaut.test)
    versionsMap.put("graalvm_native_build_tools", libs.versions.graalvm.native.build.tools)
    versionsMap.put("groovy", libs.versions.groovy)
    versionsMap.put("spock", libs.versions.spock)
    versionsMap.put("junit6", libs.versions.junit6)
    versionsMap.put("checkstyle", libs.versions.checkstyle)
    versionsMap.put("logback", libs.versions.logback)

    // Project plugins
    definePlugin("aot-module", "io.micronaut.build.aot.MicronautAotModulePlugin")
    definePlugin("base", "io.micronaut.build.MicronautBasePlugin")
    definePlugin("base-module", "io.micronaut.build.MicronautBaseModulePlugin")
    definePlugin("binary-compatibility-check", "io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin")
    definePlugin("bom", "io.micronaut.build.MicronautBomPlugin")
    definePlugin("common", "io.micronaut.build.MicronautBuildCommonPlugin")
    definePlugin("dependency-updates", "io.micronaut.build.MicronautDependencyUpdatesPlugin")
    definePlugin("develocity", "io.micronaut.build.MicronautDevelocityPlugin")
    definePlugin("docs", "io.micronaut.build.MicronautDocsPlugin")
    definePlugin("java-base", "io.micronaut.build.MicronautBuildJavaBasePlugin")
    definePlugin("kotlin-base", "io.micronaut.build.MicronautBuildKotlinBasePlugin")
    definePlugin("module", "io.micronaut.build.MicronautModulePlugin")
    definePlugin("parent", "io.micronaut.build.MicronautParentPlugin")
    definePlugin("parent-publishing", "io.micronaut.build.MicronautParentPublishingPlugin")
    definePlugin("publishing", "io.micronaut.build.MicronautPublishingPlugin")
    definePlugin("quality-checks", "io.micronaut.build.MicronautQualityChecksParticipantPlugin")
    definePlugin("quality-reporting", "io.micronaut.build.MicronautQualityReportingAggregatorPlugin")
    definePlugin("version-catalog-updates", "io.micronaut.build.catalogs.MicronautVersionCatalogUpdatePlugin")

    // Settings plugins
    definePlugin("shared-settings", "io.micronaut.build.MicronautSharedSettingsPlugin") {
        id = "io.micronaut.build.shared.settings"
    }
}
