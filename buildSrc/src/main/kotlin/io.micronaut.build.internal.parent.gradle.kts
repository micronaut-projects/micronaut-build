plugins {
    base
    id("io.github.gradle-nexus.publish-plugin")
}

val ossUser = providers.environmentVariableOrSystemProperty("SONATYPE_USERNAME", "sonatypeOssUsername").orElse("")
val ossPass = providers.environmentVariableOrSystemProperty("SONATYPE_PASSWORD", "sonatypeOssPassword").orElse("")

nexusPublishing {
    repositories {
        sonatype {
            username = ossUser
            password = ossPass
            nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
            snapshotRepositoryUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}
