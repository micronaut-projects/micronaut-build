package io.micronaut.build

import io.github.gradlenexus.publishplugin.InitializeNexusStagingRepository
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.Sign
/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautPublishingPlugin implements Plugin<Project> {


    public static final String NEXUS_STAGING_PROFILE_ID = "11bd7bc41716aa"

    @Override
    void apply(Project project) {
        def p = project.findProperty("micronautPublish")
        // add option to force publishing
        boolean doPublish = p != null && Boolean.valueOf(p.toString())
        if (!doPublish) {
            if (project.name.contains("doc") || project.name.contains("example")) {
                return
            }
        }

        project.with {
            apply plugin: 'maven-publish'
            ExtraPropertiesExtension ext = extensions.getByType(ExtraPropertiesExtension)
            def ossUser = System.getenv("SONATYPE_USERNAME") ?: project.hasProperty("sonatypeOssUsername") ? project.sonatypeOssUsername : ''
            def ossPass = System.getenv("SONATYPE_PASSWORD") ?: project.hasProperty("sonatypeOssPassword") ? project.sonatypeOssPassword : ''
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

                if (!isPlatform && project.pluginManager.hasPlugin('java')) {
                    project.task('sourcesJar', type:Jar) {
                        archiveClassifier.set('sources')
                        from project.sourceSets.main.allJava
                    }

                    project.task('javadocJar', type:Jar) {
                        archiveClassifier.set('javadoc')
                        from project.javadoc.destinationDir
                    }
                }

                publishing {
                    repositories {
                        maven {
                            name = "Build"
                            url = "${layout.buildDirectory.dir("repo").get().asFile.toURI()}"
                        }
                    }
                    publications {
                        if (project.extensions.findByType(PublishingExtension).publications.empty) {
                            maven(MavenPublication) { publication ->
                                artifactId( "micronaut-" + project.name.substring(project.name.indexOf('/') + 1) )

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
                                    def shadowJar = tasks.findByName("shadowJar")
                                    artifact(project.tasks.shadowJar) {
                                        classifier = null
                                    }
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

                                    artifact sourcesJar {
                                        classifier "sources"
                                    }
                                    artifact javadocJar {
                                        classifier "javadoc"
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
                                            afterEvaluate {
                                                artifact source: sourcesJar, classifier: "sources"
                                                artifact source: javadocJar, classifier: "javadoc"
                                            }
                                        }

                                        pom.withXml {
                                            def xml = asNode()
                                            xml.children().last() + ext.pomInfo
                                        }
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


                rootProject.plugins.apply('io.github.gradle-nexus.publish-plugin')
                NexusPublishExtension nexusPublish = rootProject.extensions.getByType(NexusPublishExtension)
                nexusPublish.with {
                    if (repositories.isEmpty()) {
                        repositoryDescription = "${project.group}:${rootProject.name}:${project.version}"
                        useStaging = !project.version.endsWith("-SNAPSHOT")
                        repositories {
                            sonatype {
                                username = ossUser
                                password = ossPass
                                nexusUrl = uri("https://s01.oss.sonatype.org/service/local/")
                                snapshotRepositoryUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                                stagingProfileId = NEXUS_STAGING_PROFILE_ID //can reduce execution time by even 10 seconds
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

}
