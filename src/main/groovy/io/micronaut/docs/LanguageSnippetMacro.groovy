package io.micronaut.docs

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.asciidoctor.ast.StructuralNode
import org.asciidoctor.extension.BlockMacroProcessor

class LanguageSnippetMacro extends BlockMacroProcessor implements ValueAtAttributes {
    final Asciidoctor asciidoctor

    private static final String LANG_JAVA = 'java'
    private static final String LANG_GROOVY = 'groovy'
    private static final String LANG_KOTLIN = 'kotlin'
    private static final List<String> LANGS = [LANG_JAVA, LANG_GROOVY, LANG_KOTLIN]
    private static final String DEFAULT_KOTLIN_PROJECT = 'test-suite-kotlin'
    private static final String DEFAULT_JAVA_PROJECT = 'test-suite'
    private static final String DEFAULT_GROOVY_PROJECT = 'test-suite-groovy'
    private static final String ATTR_PROJECT = 'project'
    private static final String ATTR_SOURCE = 'source'
    private static final String ATTR_PROJECT_BASE = 'project-base'

    LanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor) {
        super(macroName, config)
        this.asciidoctor = asciidoctor
    }

    private String projectDir(String lang, Map<String, Object> attributes) {
        String projectBase = valueAtAttributes(ATTR_PROJECT_BASE, attributes)
        if (projectBase) {
            return "$projectBase-$lang"
        }

        String project = valueAtAttributes(ATTR_PROJECT, attributes)
        if (project) {
            return project
        } else {
            if (lang == LANG_KOTLIN) {
                return DEFAULT_KOTLIN_PROJECT
            }
            if (lang == LANG_GROOVY) {
                return DEFAULT_GROOVY_PROJECT
            } else {
                return DEFAULT_JAVA_PROJECT
            }
        }
    }

    @Override
    StructuralNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String[] tags = valueAtAttributes("tags", attributes)?.toString()?.split(",")
        String indent = valueAtAttributes("indent", attributes)
        String title = valueAtAttributes("title", attributes)
        StringBuilder content = new StringBuilder()

        String[] files = target.split(",")
        for (lang in LANGS) {
            if (title != null) {
                content << ".$title\n\n"
            }
            String projectDir = projectDir(lang, attributes)
            String ext = lang == LANG_KOTLIN ? 'kt' : lang
            String sourceFolder = lang
            String sourceType = valueAtAttributes(ATTR_SOURCE, attributes) ?: 'test'

            List includes = []
            for (fileName in files) {
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

                indent = indent ? tags ? ",indent=$indent" : "indent=$indent" : ""

                if (tags) {
                    includes << tags.collect() { "include::${file.absolutePath}[tag=${it}${indent}]" }.join("\n\n")
                } else {
                    includes << "include::${file.absolutePath}[${indent}]"
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
        if (content) {
            def options = Options.builder()
                    .attributes(
                            Attributes.builder()
                                    .attribute('source-highlighter', 'highlightjs')
                                    .build()
                    )
                    .safe(SafeMode.UNSAFE)
                    .build()
            String result = asciidoctor.convert(content.toString(), options)
            return createBlock(parent, "pass", result)
        }
        return null
    }

}
