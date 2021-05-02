package io.micronaut.docs

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.asciidoctor.Asciidoctor.Factory

@CompileStatic
class PublishConfigurationReferenceTask extends DefaultTask {

    @Input
    String inputFileName

    @Input
    String destinationFileName

    @Input
    String version

    @InputFile
    File pageTemplate

    @TaskAction
    void publishConfigurationReference() {
        String textPage = pageTemplate.text
        textPage = textPage.replace("@projectVersion@", version)
        textPage = textPage.replace("@pagetitle@", 'Configuration Reference | Micronaut')
        if (new File(inputFileName).exists()) {
            try {
                String html = Factory.create().render(new File(inputFileName).text, [:])
                textPage = textPage.replace("@docscontent@", html)
                File configurationreference = new File(destinationFileName)
                configurationreference.createNewFile()
                configurationreference.text = textPage
            } catch(Exception e) {
                logger.error("Error raised rendering asciidoc file")
            }

        } else {
            logger.quiet "${inputFileName} does not exist."
        }
    }
}
