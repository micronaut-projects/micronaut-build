package io.micronaut.build

import io.micronaut.docs.gradle.PublishGuide
import io.micronaut.docs.CreateReleasesDropdownTask
import io.micronaut.docs.JavaDocAtValueReplacementTask
import io.micronaut.docs.MergeConfigurationReferenceTask
import io.micronaut.docs.ProcessConfigPropsTask
import io.micronaut.docs.PublishConfigurationReferenceTask
import io.micronaut.docs.ReplaceAtLinkTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautDocsPlugin implements Plugin<Project> {

    static final String DOCUMENTATION_GROUP = 'mndocs'
    public static final String CONFIGURATION_REFERENCE_HTML = 'configurationreference.html'
    public static final String INDEX_HTML = 'index.html'

    public static final String TASK_DELETE_INVIDUAL_PAGES = 'deleteInvidualPages'

    @Override
    void apply(Project project) {
        project.with {
            def projectVersion = project.findProperty('projectVersion')
            def projectDesc = project.findProperty('projectDesc')
            def githubSlug = project.findProperty('githubSlug')
            logger.info("Configuring micronaut documentation tasks for subprojects.")
            logger.info("Add skipDocumentation=true to a submodule gradle.properties to skip docs")
            subprojects { subproject ->
                boolean skipDocs = hasProperty('skipDocumentation') ? property('skipDocumentation') as Boolean : false
                subproject.plugins.withId('java-base') {
                    if (!skipDocs) {
                        tasks.register('moveConfigProps') { task ->
                            task.group(DOCUMENTATION_GROUP)
                            task.doLast {
                                ant.mkdir(dir: "${rootProject.buildDir}/config-props")
                                ant.move(file: "${subproject.buildDir}/classes/java/main/META-INF/config-properties.adoc", tofile: "${rootProject.buildDir}/config-props/${subproject.name}-config-properties.adoc", failonerror: false, quiet: true)
                            }
                            task.dependsOn tasks.named("classes")
                        }
                        tasks.register('javaDocAtReplacement', JavaDocAtValueReplacementTask) { task ->
                            task.group(DOCUMENTATION_GROUP)
                            task.dependsOn('moveConfigProps')
                            adoc = "${rootProject.buildDir}/config-props/${subproject.name}-config-properties.adoc"
                        }
                        tasks.register('replaceAtLink', ReplaceAtLinkTask) { task ->
                            configProperties = "${rootProject.buildDir}/config-props/${subproject.name}-config-properties.adoc"
                            task.group(DOCUMENTATION_GROUP)
                            task.dependsOn tasks.named('moveConfigProps')
                            task.mustRunAfter tasks.named('javaDocAtReplacement')
                        }

                        tasks.register('processConfigProps', ProcessConfigPropsTask) { task ->
                            configPropertiesFileName = "${rootProject.buildDir}/config-props/${subproject.name}-config-properties.adoc"
                            individualConfigPropsFolder = "${rootProject.buildDir}/generated/configurationProperties"
                            task.group(DOCUMENTATION_GROUP)
                            task.mustRunAfter tasks.named("javadoc")
                            task.mustRunAfter tasks.named("assemble")
                            task.dependsOn tasks.named('replaceAtLink')
                            task.dependsOn tasks.named('javaDocAtReplacement')
                        }
                    }
                }
            }

            tasks.register("copyLocalDocResources", Copy) { task ->
                group = DOCUMENTATION_GROUP
                description = 'Copy local resources to build folder'
                from("$project.projectDir/src/main/docs/resources")
                destinationDir = project.file("${rootProject.buildDir}/doc-resources")
            }

            configurations {
                documentation
            }

            dependencies {
                documentation("org.fusesource.jansi:jansi:1.14")
            }

            def cleanTask = tasks.findByName("clean")
            if (cleanTask == null) {
                tasks.register("clean", Delete) {
                    delete(buildDir)
                }
            } else {
                cleanTask.doLast {
                    ant.delete(dir: "build/docs")
                }
            }

            tasks.register("javadoc", Javadoc) {
                description = 'Generate javadocs from all child projects as if it was a single project'
                group = 'Documentation'

                destinationDir = file("$buildDir/docs/api")
                title = "$name ${projectVersion} API"
                options.author true
                List links = []
                for (p in properties) {
                    if (p.key.endsWith('api')) {
                        links.add(p.value.toString())
                    }
                }
                options.links links as String[]
                options.addStringOption 'Xdoclint:none', '-quiet'
                options.addBooleanOption('notimestamp', true)

                subprojects.each { proj ->
                    if (!proj.name != 'docs' && !proj.name.startsWith('examples')) {
                        boolean skipDocs = proj.hasProperty('skipDocumentation') ? proj.property('skipDocumentation') as Boolean : false

                        if (!skipDocs) {
                            proj.tasks.withType(Javadoc).each { javadocTask ->
                                source += javadocTask.source
                                classpath += javadocTask.classpath
                                excludes += javadocTask.excludes
                                includes += javadocTask.includes
                            }
                        }
                    }
                }
            }

            tasks.register('cleanupPropertyReference') { task ->
                task.group(DOCUMENTATION_GROUP)
                task.doLast {
                    File f = new File("${rootProject.buildDir}/docs/guide/${CONFIGURATION_REFERENCE_HTML}")
                    if (f.exists()) {
                        f.delete()
                    }
                }
            }
            tasks.register('mergeConfigurationReference', MergeConfigurationReferenceTask) { task ->
                inputFileName = "${rootProject.buildDir}/generated/propertyReference.adoc"
                inputFilesName = "${buildDir}/config-props"
                task.onlyIf {
                    !fileTree("$buildDir/config-props").isEmpty()
                }
                task.group(DOCUMENTATION_GROUP)
            }
            tasks.register('publishConfigurationReference', PublishConfigurationReferenceTask) { task ->
                inputFileName = "${rootProject.buildDir}/generated/propertyReference.adoc"
                destinationFileName = "${rootProject.buildDir}/docs/guide/${CONFIGURATION_REFERENCE_HTML}"
                version = projectVersion
                pageTemplate = file("${rootProject.buildDir}/doc-resources/style/page.html")
                task.dependsOn tasks.named('mergeConfigurationReference')
                task.mustRunAfter tasks.named('publishGuide')
            }
            tasks.register('cleanupGuideFiles', Delete) { task ->
                task.group(DOCUMENTATION_GROUP)
                delete fileTree("${rootProject.buildDir}/docs/guide") {
                    include '*.html'
                    exclude INDEX_HTML
                    exclude publishConfigurationReference.destinationFileName
                }
                delete fileTree("${rootProject.buildDir}/docs/guide/pages") {
                    include '*.html'
                }
            }

            tasks.register(TASK_DELETE_INVIDUAL_PAGES, Delete) {
                group = DOCUMENTATION_GROUP
                description = "delete HTML files under build/docs/guides except ${INDEX_HTML} and ${CONFIGURATION_REFERENCE_HTML}"
                delete fileTree("${buildDir}/docs/guide") {
                    include '**/*.html'
                    exclude CONFIGURATION_REFERENCE_HTML
                    exclude INDEX_HTML
                }
            }
            tasks.register('publishGuide', PublishGuide) {
                group = DOCUMENTATION_GROUP
                description = 'Generate Guide'
                dependsOn copyLocalDocResources

                targetDir = file("${buildDir}/docs")
                String githubBranch = 'git rev-parse --abbrev-ref HEAD'.execute()?.text?.trim() ?: 'master'
                sourceRepo = "https://github.com/${githubSlug}/edit/${githubBranch}/src/main/docs"
                sourceDir = new File(projectDir, "src/main/docs")
                def f = new File(project.projectDir, 'src/main/docs/resources')
                if (f.exists()) {
                    resourcesDir = f
                } else {
                    f = new File(project.buildDir, 'doc-resources')
                    f.mkdirs()
                    resourcesDir = f
                }
                propertiesFiles = [new File(rootProject.projectDir, "gradle.properties")]
                asciidoc = true
                properties = [
                        'safe': 'UNSAFE',
                        'source-highlighter': 'highlightjs',
                        'version': projectVersion,
                        'subtitle': projectDesc,
                        'github': 'https://github.com/micronaut-projects/micronaut-core',
                        'api': '../api',
                        'micronautapi': 'https://docs.micronaut.io/latest/api/io/micronaut/',
                        'sourceDir': rootProject.projectDir.absolutePath,
                        'sourcedir': rootProject.projectDir.absolutePath,
                        'includedir': "${rootProject.buildDir.absolutePath}/generated/",
                        'javaee': 'https://docs.oracle.com/javaee/8/api/',
                        'javase': 'https://docs.oracle.com/javase/8/docs/api/',
                        'groovyapi': 'http://docs.groovy-lang.org/latest/html/gapi/',
                        'grailsapi': 'http://docs.grails.org/latest/api/',
                        'gormapi': 'http://gorm.grails.org/latest/api/',
                        'springapi': 'https://docs.spring.io/spring/docs/current/javadoc-api/',
                        'kafka-version': rootProject.hasProperty('kafkaVersion') ? rootProject.properties['kafkaVersion'] : 'N/A'
                ]
                doLast {
                    ant.move(file: "${buildDir}/docs/guide/single.html",
                            tofile: "${buildDir}/docs/guide/${INDEX_HTML}", overwrite: true)
                    new File(buildDir, "docs/${INDEX_HTML}").text = """
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<meta http-equiv="refresh" content="0; url=guide/${INDEX_HTML}" />
</head>

</body>
</html>
"""
                }
                finalizedBy(TASK_DELETE_INVIDUAL_PAGES)
            }

            tasks.register("zipDocs", Zip) { task ->
                task.group(DOCUMENTATION_GROUP)
                archiveBaseName = "${name}-${projectVersion}"
                destinationDirectory = new File(buildDir, "distributions")
                from files("${buildDir}/docs")
            }
            // TODO: Don't do this
            tasks.register('assemble') {
                subprojects.each { subproject ->
                    if (subproject.tasks.findByName("assemble") != null) {
                        dependsOn subproject.tasks.findByName("assemble")
                    }
                }
            }

            def processConfigPropsTask = tasks.register('processConfigProps')
            // TODO: Reaching out to other project's state is not a good practice,
            // this needs to be replaced
            subprojects.each { subproject ->
                subproject.tasks.configureEach {
                    if ("processConfigProps" == it.name) {
                        processConfigPropsTask.dependsOn(it)
                    }
                }
            }


            tasks.register("createReleasesDropdown", CreateReleasesDropdownTask) { task ->
                slug = githubSlug as String
                version = projectVersion
                doc = file("${buildDir.absolutePath}/docs/guide/${INDEX_HTML}")
                task.mustRunAfter tasks.named("zipDocs")
                task.onlyIf {
                    new File("${buildDir.absolutePath}/docs/guide/${INDEX_HTML}").exists()
                }
            }


            tasks.register("docs") { task ->
                task.dependsOn tasks.named("assemble")
                task.dependsOn tasks.named("javadoc")
                task.dependsOn tasks.named("publishGuide")
                task.dependsOn tasks.named("processConfigProps")
                task.dependsOn tasks.named("publishConfigurationReference")
                task.finalizedBy tasks.named("zipDocs")
                task.finalizedBy tasks.named("createReleasesDropdown")
            }
            tasks.named('javadoc', Javadoc) {
                exclude "example/**"
                mustRunAfter tasks.assemble
            }
            tasks.named('publishGuide') {
                mustRunAfter processConfigPropsTask
            }
        }
    }
}
