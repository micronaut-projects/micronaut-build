package io.micronaut.build

import io.micronaut.build.docs.ConfigurationPropertiesPlugin
import io.micronaut.build.docs.CreateReleasesDropdownTask
import io.micronaut.build.docs.JavadocAggregatorPlugin
import io.micronaut.build.docs.PrepareDocResourcesTask
import io.micronaut.build.docs.PublishGuideTask
import io.micronaut.build.docs.ValidateAsciidocOutputTask
import io.micronaut.build.docs.props.MergeConfigurationReferenceTask
import io.micronaut.build.docs.props.PublishConfigurationReferenceTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
class MicronautDocsPlugin implements Plugin<Project> {

    static final String DOCUMENTATION_GROUP = 'mndocs'
    public static final String CONFIGURATION_REFERENCE_HTML = 'configurationreference.html'
    public static final String INDEX_HTML = 'index.html'

    @Override
    void apply(Project project) {
        project.with {
            plugins.apply(BasePlugin)
            plugins.apply(JavadocAggregatorPlugin)
            def projectVersion = project.findProperty('projectVersion')
            def projectDesc = project.findProperty('projectDesc')
            def githubSlug = project.findProperty('githubSlug')
            logger.info("Configuring micronaut documentation tasks for subprojects.")
            logger.info("Add skipDocumentation=true to a submodule gradle.properties to skip docs")
            def configProperties = configurations.create("configProperties") {
                it.canBeConsumed = false
                it.canBeResolved = false
            }
            def incomingIndividualConfigProps = configurations.create("incomingIndividualConfigProps") {
                it.canBeConsumed = false
                it.canBeResolved = true
                it.extendsFrom(configProperties)
                it.attributes {
                    ConfigurationPropertiesPlugin.configureAttributes(it, project.objects, ConfigurationPropertiesPlugin.INDIVIDUAL_CONFIGURATION_PROPERTIES)
                }
            }
            def incomingConfigProps = configurations.create("incomingConfigProps") {
                it.canBeConsumed = false
                it.canBeResolved = true
                it.extendsFrom(configProperties)
                it.attributes {
                    ConfigurationPropertiesPlugin.configureAttributes(it, project.objects, ConfigurationPropertiesPlugin.CONFIGURATION_PROPERTIES)
                }
            }
            subprojects { subproject ->
                subproject.plugins.withType(ConfigurationPropertiesPlugin) {
                    boolean skipDocs = hasProperty('skipDocumentation') ? property('skipDocumentation') as Boolean : false
                    if (!skipDocs) {
                        configProperties.dependencies.add(dependencies.create(subproject))
                    }
                }
            }

            configurations {
                documentation
            }

            dependencies {
                documentation("org.fusesource.jansi:jansi:1.14")
            }

            def cleanDocs = tasks.register("cleanDocs", Delete) {
                delete layout.buildDirectory.dir("docs")
            }

            tasks.named('clean', Delete) {
                dependsOn(cleanDocs)
            }

            def prepareDocsResources = tasks.register('prepareDocsResources', PrepareDocResourcesTask) {
                group = DOCUMENTATION_GROUP
                description = 'Prepare resources for documentation'
                resources.from(layout.projectDirectory.dir("src/main/docs/resources"))
                outputDirectory = layout.buildDirectory.dir("doc-resources")
                resourceClasspathJarName = "grails-doc-files.jar"
            }

            def processConfigPropsTask = tasks.register('processConfigProps', Copy) {
                from(incomingIndividualConfigProps)
                into(layout.buildDirectory.dir("working/01-includes/configurationProperties"))
            }

            def publishGuide = tasks.register('publishGuide', PublishGuideTask) {
                group = DOCUMENTATION_GROUP
                description = 'Generate Guide'

                def kafkaVersion = rootProject.hasProperty('kafkaVersion') ? rootProject.properties['kafkaVersion'] : 'N/A'

                inputs.files(processConfigPropsTask)
                inputs.property("Project description", projectDesc)
                inputs.property("Kafka version", kafkaVersion)

                targetDir = layout.buildDirectory.dir("working/02-docs-raw")
                String githubBranch = 'git rev-parse --abbrev-ref HEAD'.execute()?.text?.trim() ?: 'master'
                sourceRepo = "https://github.com/${githubSlug}/edit/${githubBranch}/src/main/docs"
                sourceDir = layout.projectDirectory.dir("src/main/docs")
                resourcesDir = prepareDocsResources.flatMap(PrepareDocResourcesTask::getOutputDirectory)
                propertiesFiles.from(rootProject.file("gradle.properties"))
                asciidoc = true
                properties.putAll([
                        'safe': 'UNSAFE',
                        'source-highlighter': 'highlightjs',
                        'version': projectVersion,
                        'subtitle': projectDesc,
                        'github': 'https://github.com/micronaut-projects/micronaut-core',
                        'api': '../api',
                        'micronautapi': 'https://docs.micronaut.io/latest/api/io/micronaut/',
                        'sourceDir': rootProject.projectDir.absolutePath,
                        'sourcedir': rootProject.projectDir.absolutePath,
                        'includedir': "${processConfigPropsTask.get().destinationDir.parentFile}/",
                        'javaee': 'https://docs.oracle.com/javaee/8/api/',
                        'javase': 'https://docs.oracle.com/javase/8/docs/api/',
                        'groovyapi': 'http://docs.groovy-lang.org/latest/html/gapi/',
                        'grailsapi': 'http://docs.grails.org/latest/api/',
                        'gormapi': 'http://gorm.grails.org/latest/api/',
                        'springapi': 'https://docs.spring.io/spring/docs/current/javadoc-api/',
                        'kafka-version': kafkaVersion
                ])
            }

            def mergeConfigurationReference = tasks.register('mergeConfigurationReference', MergeConfigurationReferenceTask) { task ->
                inputFiles.from(incomingConfigProps)
                outputFile = layout.buildDirectory.file("working/03-property-ref/adoc/propertyReference.adoc")
                task.group(DOCUMENTATION_GROUP)
            }

            tasks.register('publishConfigurationReference', PublishConfigurationReferenceTask) { task ->
                propertyReferenceFile = mergeConfigurationReference.flatMap { it.outputFile }
                destinationFile = layout.buildDir.file("working/03-property-ref/html/${CONFIGURATION_REFERENCE_HTML}")
                version = projectVersion
                pageTemplate.set(publishGuide.map { it.resourcesDir.file("style/page.html").get() })
            }

            def assembleDocs = tasks.register("assembleDocs", Sync) {
                description = "Assembles the documentation"
                destinationDir = layout.buildDirectory.dir("working/04-assembled-docs").get().asFile
                from(publishGuide.flatMap { it.targetDir }) {
                    include { FileTreeElement e ->
                        def relativePath = e.relativePath.pathString
                        if (relativePath.startsWith('guide/')) {
                            return relativePath == 'guide/index.html'
                        }
                        true
                    }
                }
                from(publishConfigurationReference) {
                    into 'guide'
                }
                from(tasks.withType(Javadoc)) {
                    into 'api'
                }
            }

            def validateAssembledDocs = tasks.register("validateAssembleDocs", ValidateAsciidocOutputTask) {
                inputDirectory.fileProvider(assembleDocs.map { it.destinationDir })
                report = layout.buildDirectory.file("working/reports/assemble-docs.txt")
            }

            assembleDocs.configure {
                finalizedBy(validateAssembledDocs)
            }

            def zipDocs = tasks.register("zipDocs", Zip) { task ->
                task.group(DOCUMENTATION_GROUP)
                archiveAppendix = 'docs'
                destinationDirectory = new File(buildDir, "distributions")
                from(assembleDocs)
            }

            def createReleasesDropdown = tasks.register("createReleasesDropdown", CreateReleasesDropdownTask) { task ->
                task.group(DOCUMENTATION_GROUP)
                slug = githubSlug as String
                version = projectVersion
                sourceIndex = publishGuide.flatMap { it.targetDir.file("guide/index.html") }
                outputIndex = layout.buildDir.file("working/05-dropdown/index.html")
                versionsJson = providers.provider {
                    String url = "https://api.github.com/repos/${slug.get()}/tags"
                    try {
                        return new URL(url).text
                    } catch(IOException e) {
                        logger.error("IOException fetching " + url)
                        return "[]"
                    }
                }
            }

            def assembleFinalDocs = tasks.register("assembleFinalDocs", Copy) {
                destinationDir = layout.buildDirectory.dir("docs").get().asFile
                from(assembleDocs) {
                    exclude 'guide/index.html'
                }
                into('guide') {
                    from(createReleasesDropdown)
                }
            }

            tasks.register("docs") { task ->
                task.dependsOn assembleDocs
                task.dependsOn assembleFinalDocs
                task.dependsOn zipDocs
            }
        }
    }
}
