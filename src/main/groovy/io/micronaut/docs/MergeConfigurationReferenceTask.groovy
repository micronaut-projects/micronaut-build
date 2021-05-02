package io.micronaut.docs

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@CompileStatic
class MergeConfigurationReferenceTask extends DefaultTask {

    @Input
    String inputFilesName

    @Input
    String inputFileName

    @TaskAction
    void mergeConfigurationReferenceFiles() {
        Set<File> inputFiles = project.fileTree(inputFilesName).files
        File outputFile = new File(inputFileName)
        outputFile.createNewFile()
        outputFile.withOutputStream { out ->
            List<File> files = new ArrayList<>(inputFiles).sort { Object obj -> ((File)obj).name } as List<File>
            for ( file in files ) {
                String header = "=== " + file.name.replace('.adoc', '').split('-').collect { String token ->
                    "${token.charAt(0).toString().toUpperCase()}${token.substring(1)}"
                }.join(' ')
                file.withInputStream {
                    out << header << '\n' << it << '\n'
                }
            }
        }
    }
}
