package io.micronaut.docs

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

@CompileStatic
class ReplaceAtLinkTask extends DefaultTask {

    private static final Closure atLinkReplacer = { String str ->
        String newLine = str.substring(0, str.indexOf('{@link io.micronaut.'))
        String sub = "api:"+str.substring(str.indexOf('{@link io.micronaut.') + '{@link io.micronaut.'.length())
        newLine += sub.substring(0, sub.indexOf('}')) + '[]'
        newLine += sub.substring(sub.indexOf('}') + '}'.length())
        newLine
    }

    @Input
    String configProperties

    @TaskAction
    void downloadResources() {
        File configPropertiesFile = new File(configProperties)
        if (configPropertiesFile.exists()) {
            List<String> lines = configPropertiesFile.readLines()
            List<String> outputLines = []
            for (String line : lines) {
                String proccessedLine = line
                while (proccessedLine.contains('{@link io.micronaut.')) {
                    proccessedLine = atLinkReplacer(proccessedLine)
                }
                outputLines << proccessedLine
            }
            configPropertiesFile.text = outputLines.join('\n')
        }

    }
}
