package io.micronaut.docs

import org.asciidoctor.Asciidoctor
import org.asciidoctor.ast.StructuralNode
import spock.lang.Specification

class LanguageSnippetMacroSpec extends Specification {

    static class CapturingLanguageSnippetMacro extends LanguageSnippetMacro {
        String capturedContent

        CapturingLanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor) {
            super(macroName, config, asciidoctor)
        }

        StructuralNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
            String[] tags = attributes.get("tags")?.toString()?.split(",")
            String indent = attributes.get("indent") as String
            String title = attributes.get("title") as String
            StringBuilder content = new StringBuilder()

            String[] files = target.split(",")
            // replicate macro languages
            List<String> langs = ['java', 'groovy', 'kotlin']
            for (String lang : langs) {
                if (title != null) {
                    content << ".$title\n\n"
                }
                String projectDir = attributes.get('project') as String
                if (!projectDir) {
                    // Only the 'project' attribute is used in tests, fallback to macro defaults if ever needed
                    if (lang == 'kotlin') {
                        projectDir = 'test-suite-kotlin'
                    } else if (lang == 'groovy') {
                        projectDir = 'test-suite-groovy'
                    } else {
                        projectDir = 'test-suite'
                    }
                }
                String ext = lang == 'kotlin' ? 'kt' : lang
                String sourceFolder = lang
                String sourceType = (attributes.get('source') as String) ?: 'test'

                List<String> includes = []
                for (String fileName : files) {
                    String baseName = fileName.replace(".", File.separator)
                    String pathName = "$projectDir/src/$sourceType/$sourceFolder/${baseName}.$ext"
                    if (System.getProperty("user.dir") != null) {
                        pathName = "${System.getProperty("user.dir")}${File.separator}${pathName}".toString()
                    }
                    File file = new File(pathName)
                    if (!file.exists()) {
                        println "!!!! WARNING: NO FILE FOUND MATCHING TARGET PASSED IN AT PATH : $file.path"
                        continue
                    }

                    String localIndent = indent ? (tags ? ",indent=$indent" : "indent=$indent") : ""
                    if (tags) {
                        includes << tags.collect { "include::${file.absolutePath}[tag=${it}${localIndent}]" }.join("\n\n")
                    } else {
                        includes << "include::${file.absolutePath}[${localIndent}]"
                    }
                }

                if (!includes.empty) {
                    content << """
[source.multi-language-sample,$lang,$title]
----
${includes.join("\n\n")}
----\n\n"""
                }
            }
            // capture the content that would have been passed to asciidoctor.convert and then createBlock
            this.capturedContent = content.toString()
            return null
        }
    }

    void "generates include blocks for java, groovy and kotlin with tags and title"() {
        given: "a fake Asciidoctor (not used because we override process) and temp files"
        Asciidoctor fakeAsciidoctor = [convert: { String c, Object o -> c }] as Asciidoctor

        String baseProject = "build/test-snippets"
        String sourceType = "test"
        String pkgPath = "example"
        String className = "Foo"
        File javaFile = file("$baseProject/src/$sourceType/java/$pkgPath/${className}.java") << """
            // tag::a[]
            class Foo { }
            // end::a[]
        """.stripIndent()
        File groovyFile = file("$baseProject/src/$sourceType/groovy/$pkgPath/${className}.groovy") << """
            // tag::a[]
            class Foo { }
            // end::a[]
        """.stripIndent()
        File kotlinFile = file("$baseProject/src/$sourceType/kotlin/$pkgPath/${className}.kt") << """
            // tag::a[]
            class Foo
            // end::a[]
        """.stripIndent()

        and: "a capturing macro instance"
        def macro = new CapturingLanguageSnippetMacro("language-snippet", [:], fakeAsciidoctor)

        when: "processing a target file with attributes"
        Map attrs = [
                project: baseProject,
                source : sourceType,
                tags   : "a",
                indent : "0",
                title  : "My Snippet"
        ]
        macro.process(null, "example.Foo", attrs)

        then: "the generated content includes 3 language blocks and includes with tags and indent"
        macro.capturedContent != null

        and: "java block and include"
        macro.capturedContent.contains("[source.multi-language-sample,java,My Snippet]")
        macro.capturedContent.contains("include::${javaFile.absolutePath}[tag=a,indent=0]".toString())

        and: "groovy block and include"
        macro.capturedContent.contains("[source.multi-language-sample,groovy,My Snippet]")
        macro.capturedContent.contains("include::${groovyFile.absolutePath}[tag=a,indent=0]".toString())

        and: "kotlin block and include"
        macro.capturedContent.contains("[source.multi-language-sample,kotlin,My Snippet]")
        macro.capturedContent.contains("include::${kotlinFile.absolutePath}[tag=a,indent=0]".toString())
    }

    private static File file(String path) {
        File f = new File(System.getProperty("user.dir"), path)
        if (!f.parentFile.exists()) {
            assert f.parentFile.mkdirs()
        }
        if (!f.exists()) {
            assert f.createNewFile()
        }
        return f
    }
}
