package io.micronaut.build

import io.github.gradlenexus.publishplugin.InitializeNexusStagingRepository
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.plugins.signing.Sign

import static io.micronaut.build.MicronautPlugin.PRE_RELEASE_CHECK_TASK_NAME
import static io.micronaut.build.MicronautPlugin.moduleNameOf

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautPublishingPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        configurePreReleaseCheck(project)
        def p = project.findProperty("micronautPublish")
        // add option to skip publishing
        if (p == null) {
            if (project.name.contains("doc") || project.name.contains("example")) {
                return
            }
        } else {
            boolean doPublish = Boolean.valueOf(p.toString())
            if (!doPublish) {
                return
            }
        }

        project.pluginManager.apply(MavenPublishPlugin)

        def ossUser = System.getenv("SONATYPE_USERNAME") ?: project.hasProperty("sonatypeOssUsername") ? project.sonatypeOssUsername : ''
        def ossPass = System.getenv("SONATYPE_PASSWORD") ?: project.hasProperty("sonatypeOssPassword") ? project.sonatypeOssPassword : ''

        project.with {
            plugins.withId('java-base') {
                java {
                    withSourcesJar()
                    withJavadocJar()
                }
                micronautBuild.environment.duringMigration {
                    tasks.withType(Javadoc).configureEach {
                        // temporary workaround for broken docs in many modules
                        failOnError = false
                    }
                    ['sourcesJar', 'javadocJar'].each { name ->
                        tasks.named(name, Jar) {
                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        }
                    }
                }
            }
            ExtraPropertiesExtension ext = extensions.getByType(ExtraPropertiesExtension)
            ext."signing.keyId" = System.getenv("GPG_KEY_ID") ?: project.hasProperty("signing.keyId") ? project.getProperty('signing.keyId') : null
            ext."signing.password" = System.getenv("GPG_PASSWORD") ?: project.hasProperty("signing.password") ? project.getProperty('signing.password') : null
            def githubSlug = project.findProperty('githubSlug')

            ext.pomInfo = {
                if (project.extensions.getByType(ExtraPropertiesExtension).has('startPomInfo')) {
                    ext.startPomInfo.delegate = delegate
                    ext.startPomInfo.call()
                }
                delegate.name project.title
                delegate.description project.projectDesc
                delegate.url project.findProperty('projectUrl')

                delegate.licenses {
                    delegate.license {
                        delegate.name 'The Apache Software License, Version 2.0'
                        delegate.url 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                        delegate.distribution 'repo'
                    }
                }

                delegate.scm {
                    delegate.url "scm:git@github.com:${githubSlug}.git"
                    delegate.connection "scm:git@github.com:${githubSlug}.git"
                    delegate.developerConnection "scm:git@github.com:${githubSlug}.git"
                }

                if(project.hasProperty('developers')) {
                    delegate.developers {
                        for(dev in project.findProperty('developers').split(',')) {
                            delegate.developer {
                                delegate.id dev.toLowerCase().replace(' ', '')
                                delegate.name dev
                            }
                        }
                    }
                }
                if (project.extensions.getByType(ExtraPropertiesExtension).has('extraPomInfo')) {
                    ext.extraPomInfo.delegate = delegate
                    ext.extraPomInfo.call()
                }
            }

            afterEvaluate {
                boolean isPlatform = project.plugins.findPlugin("java-platform") != null

                publishing {
                    repositories {
                        def externalRepo = providers.systemProperty("io.micronaut.publishing.uri").orNull
                        if (externalRepo) {

                            def externalRepoUsername = providers.systemProperty("io.micronaut.publishing.username").orNull
                            def externalRepoPassword = providers.systemProperty("io.micronaut.publishing.password").orNull

                            maven {
                                name = "External"
                                url = externalRepo
                                if(externalRepoUsername){
                                    credentials {
                                        username = externalRepoUsername
                                        password = externalRepoPassword
                                    }
                                }
                            }
                        }

                        maven {
                            name = "Build"
                            url = "${rootProject.layout.buildDirectory.dir("repo").get().asFile.toURI()}"
                        }

                    }
                    publications {
                        String aid = moduleNameOf(project.name.substring(project.name.indexOf('/') + 1))
                        if (project.extensions.findByType(PublishingExtension).publications.empty) {
                            maven(MavenPublication) { publication ->
                                artifactId( aid )

                                if (!project.name.endsWith("bom")) {
                                    versionMapping {
                                        usage('java-api') {
                                            fromResolutionOf('runtimeClasspath')
                                        }
                                        usage('java-runtime') {
                                            fromResolutionResult()
                                        }
                                    }
                                }

                                if(project.hasProperty('shadowJarEnabled') && project.shadowJarEnabled == true) {
                                    // TODO: This code doesn't use Gradle publications, it hard codes publishing
                                    // which is easy to break and causes Gradle Module Metadata to be ignored
                                    // this should be replaced with a publication
                                    def shadowJar = tasks.named("shadowJar")
                                    artifact(shadowJar) {
                                        classifier = null
                                    }
                                    artifact(tasks.named('javadocJar'))
                                    artifact(tasks.named('sourcesJar'))
                                    pom.withXml { xml ->
                                        def xmlNode = xml.asNode()
                                        def dependenciesNode = xmlNode.appendNode('dependencies')
                                        Set<Dependency> visited = new HashSet<>()

                                        project.configurations.api.allDependencies.each {
                                            if (!(it instanceof SelfResolvingDependency)) {
                                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                                dependencyNode.appendNode('groupId', it.group)
                                                dependencyNode.appendNode('artifactId', it.name)
                                                dependencyNode.appendNode('version', it.version)
                                                dependencyNode.appendNode('scope', 'compile')
                                            } else if (it instanceof ProjectDependency) {
                                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                                dependencyNode.appendNode('groupId', project.group)
                                                dependencyNode.appendNode('artifactId', "micronaut-$it.name")
                                                dependencyNode.appendNode('version', project.version)
                                                dependencyNode.appendNode('scope', 'compile')
                                            }
                                            visited.add(it)
                                        }
                                        def runtimeHandler = {
                                            if (visited.contains(it)) {
                                                return
                                            }
                                            if (!(it instanceof SelfResolvingDependency)) {
                                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                                dependencyNode.appendNode('groupId', it.group)
                                                dependencyNode.appendNode('artifactId', it.name)
                                                dependencyNode.appendNode('version', it.version)
                                                dependencyNode.appendNode('scope', 'runtime')
                                            } else if (it instanceof ProjectDependency) {
                                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                                dependencyNode.appendNode('groupId', project.group)
                                                dependencyNode.appendNode('artifactId', "micronaut-$it.name")
                                                dependencyNode.appendNode('version', project.version)
                                                dependencyNode.appendNode('scope', 'runtime')

                                            }
                                            visited.add(it)
                                        }
                                        project.configurations.implementation.allDependencies.each (runtimeHandler)
                                        project.configurations.runtimeOnly.allDependencies.each (runtimeHandler)
                                    }

                                    pom.withXml {
                                        def xml = asNode()

                                        xml.children().last() + pomInfo
                                    }
                                } else {
                                    if (isPlatform) {
                                        from components.javaPlatform
                                        pom.withXml {
                                            def xml = asNode()

                                            xml.children().find {
                                                it.name().localPart == 'packaging'
                                            } + ext.pomInfo
                                        }
                                    } else {
                                        if (components.findByName('java')) {
                                            from components.java
                                        }

                                        pom.withXml {
                                            def xml = asNode()
                                            xml.children().last() + ext.pomInfo
                                        }
                                    }
                                }

                            }
                        }
                        // Include a pom.xml file into the jar
                        // so that automated vulnerability scanners are happy
                        tasks.withType(Jar).configureEach {
                            if (it.name in ['jar', 'shadowJar']) {
                                into("META-INF/maven/${project.group}/${aid}") {
                                    from(tasks.named("generatePomFileForMavenPublication")) {
                                        rename("pom-default.xml", "pom.xml")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (file("${rootDir}/secring.gpg").exists()) {
                ext."signing.secretKeyRingFile" = file("${rootDir}/secring.gpg").absolutePath
            } else if (file("${System.getenv('HOME')}/.gnupg/secring.gpg").exists()){
                ext."signing.secretKeyRingFile" = file("${System.getenv('HOME')}/.gnupg/secring.gpg").absolutePath
            }

            if (ossUser && ossPass) {
                if (ext."signing.keyId" && ext."signing.password") {
                    apply plugin: 'signing'
                    afterEvaluate {
                        if (project.extensions.findByType(PublishingExtension).publications.findByName('maven')) {
                            signing {
                                required { !project.version.endsWith("-SNAPSHOT") && !project.hasProperty("skipSigning") }
                                sign publishing.publications.maven
                            }
                            tasks.withType(Sign) {
                                onlyIf { !project.version.endsWith("-SNAPSHOT") }
                            }
                        }
                    }
                }

                //do not generate extra load on Nexus with new staging repository if signing fails
                tasks.withType(InitializeNexusStagingRepository).configureEach {
                    if (!tasks.withType(Sign).empty) {
                        shouldRunAfter(tasks.withType(Sign))
                    }
                }

            }
        }
    }

    private void configurePreReleaseCheck(Project project) {
        project.tasks.register(PRE_RELEASE_CHECK_TASK_NAME) {
            it.description = "Performs pre-release checks"
        }
    }
}
