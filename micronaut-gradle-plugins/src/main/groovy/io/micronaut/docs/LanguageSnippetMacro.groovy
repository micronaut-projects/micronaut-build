package io.micronaut.docs

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.asciidoctor.ast.StructuralNode
import org.asciidoctor.extension.BlockMacroProcessor

public class LanguageSnippetMacro extends BlockMacroProcessor implements ValueAtAttributes {
    final Asciidoctor asciidoctor

    private static final String LANG_JAVA = 'java'
    private static final String LANG_GROOVY = 'groovy'
    private static final String LANG_KOTLIN = 'kotlin'
    private static final String LANG_PYTHON = 'python'
    public static final List<String> LANGS = [LANG_JAVA, LANG_PYTHON, LANG_KOTLIN, LANG_GROOVY]

    LanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor) {
        super(macroName, config)
        this.asciidoctor = asciidoctor
    }

    private File snippetFile(StructuralNode parent, String lang, String fileName, Map<String, Object> attributes) {
        String sourceDir = parent.document.getAttribute('sourcedir') ?: parent.document.getAttribute('sourceDir')
        File baseDir = sourceDir ? new File(sourceDir) : System.getProperty("user.dir") ? new File(System.getProperty("user.dir")) : new File("")
        SnippetSourceResolver.resolveSnippetFile(baseDir, lang, fileName, attributes)
    }

    @Override
    StructuralNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String[] tags = valueAtAttributes("tags", attributes)?.toString()?.split(",")
        String indent = valueAtAttributes("indent", attributes)
        String title = valueAtAttributes("title", attributes)
        StringBuilder content = new StringBuilder()

        // Determine the target guide language (if any) from the Asciidoctor document attributes.
        // MicronautDocsPlugin sets 'default-language' as an engine property when generating
        // language-specific guides, and AsciiDocEngine forwards engine properties as document attributes.
        String defaultLanguage = parent.document.getAttribute('default-language')

        // If a specific target language is requested and it is one of the known code languages,
        // only render that language, and do NOT use the multi-language-sample selector class.
        List<String> languagesToRender = LANGS
        if (defaultLanguage && LANGS.contains(defaultLanguage)) {
            languagesToRender = [defaultLanguage]
        }

        String[] files = target.split(",")
        for (lang in languagesToRender) {
            if (title != null) content << ".$title\n\n"

            List includes = []
            for (fileName in files) {
                File file = snippetFile(parent, lang, fileName, attributes)
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
