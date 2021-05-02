package io.micronaut.docs

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.TaskAction

@CompileStatic
class CleanDocResourcesTask extends DefaultTask {

    @Destroys
    List<File> resourceFolders

    @TaskAction
    void cleanResources() {
        for(File f : resourceFolders) {
            if (f.exists()) {
                project.getLogger().info("deleting dir ${f.name}")
                [
                        css  : MicronautDocsResources.CSS,
                        img  : MicronautDocsResources.IMG,
                        js   : MicronautDocsResources.JS,
                        style: MicronautDocsResources.STYLE,
                ].each { String k, List<String> v ->
                    v.each { name ->
                        String path = "${f.absolutePath}/$name"
                        File resourceFile = new File(path)
                        if (resourceFile.exists()) {
                            resourceFile.delete()
                        }
                    }
                }
                boolean dirIsEmpty = f.listFiles().length == 0
                if (dirIsEmpty) {
                    f.deleteDir()
                }
            }
        }
    }
}
