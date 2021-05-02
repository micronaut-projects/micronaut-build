package io.micronaut.docs

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

@CompileStatic
class ProcessConfigPropsTask extends DefaultTask {
    static final String SEPARATOR = "<<<"
    static final String ID = 'id="'
    static final String ANCHOR_WITH_ID = '<a ' + ID
    static final String DOUBLE_QUOTE = '"'

    @Input
    String configPropertiesFileName

    @Input
    String individualConfigPropsFolder

    @TaskAction
    void foo() {
        File configProperties = new File(configPropertiesFileName)
        if (configProperties.exists()) {
            List<String> lines = configProperties.readLines()
            List<String> accumulator = []
            String configurationPropertyName = ''
            for (String line : lines) {

                if (line.startsWith(ANCHOR_WITH_ID)) {
                    String sub = line.substring(line.indexOf(ID) + ID.length())
                    sub = sub.substring(0, sub.indexOf(DOUBLE_QUOTE))
                    configurationPropertyName = sub
                }
                if (line == SEPARATOR) {
                    File folder = new File(individualConfigPropsFolder)
                    folder.mkdirs()
                    File outputfile = new File("${folder.absolutePath}/${configurationPropertyName}.adoc")
                    outputfile.createNewFile()
                    outputfile.text = accumulator.join('\n')
                    accumulator = []
                    configurationPropertyName = null
                } else {
                    accumulator << line
                }
            }
        } else {
            logger.quiet "${configPropertiesFileName} does not exist."
        }

    }
}
