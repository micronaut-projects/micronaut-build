package io.micronaut.docs.macros;

import io.micronaut.docs.SnippetSourceResolver;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.BlockMacroProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LanguageSnippetMacro extends BlockMacroProcessor implements ValueAtAttributes {
    private static final String LANG_JAVA = "java";
    private static final String LANG_GROOVY = "groovy";
    private static final String LANG_KOTLIN = "kotlin";
    private static final String LANG_PYTHON = "python";
    public static final List<String> LANGS = List.of(LANG_JAVA, LANG_PYTHON, LANG_KOTLIN, LANG_GROOVY);

    private final Asciidoctor asciidoctor;

    public LanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor) {
        super(macroName, config);
        this.asciidoctor = asciidoctor;
    }

    private File snippetFile(StructuralNode parent, String lang, String fileName, Map<String, Object> attributes) {
        Object sourceDirAttribute = parent.getDocument().getAttribute("sourcedir");
        if (sourceDirAttribute == null) {
            sourceDirAttribute = parent.getDocument().getAttribute("sourceDir");
        }
        File baseDir = sourceDirAttribute != null
            ? new File(sourceDirAttribute.toString())
            : System.getProperty("user.dir") != null ? new File(System.getProperty("user.dir")) : new File("");
        return SnippetSourceResolver.resolveSnippetFile(baseDir, lang, fileName, attributes);
    }

    @Override
    public StructuralNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String tagsAttribute = valueAtAttributes("tags", attributes);
        String[] tags = tagsAttribute == null ? null : tagsAttribute.split(",");
        String indent = valueAtAttributes("indent", attributes);
        String title = valueAtAttributes("title", attributes);
        StringBuilder content = new StringBuilder();

        Object defaultLanguageAttribute = parent.getDocument().getAttribute("default-language");
        String defaultLanguage = defaultLanguageAttribute == null ? null : defaultLanguageAttribute.toString();
        List<String> languagesToRender = LANGS;
        if (defaultLanguage != null && LANGS.contains(defaultLanguage)) {
            languagesToRender = List.of(defaultLanguage);
        }

        String[] files = target.split(",");
        for (String lang : languagesToRender) {
            if (title != null) {
                content.append(".").append(title).append("\n\n");
            }

            List<String> includes = new ArrayList<>();
            for (String fileName : files) {
                File file = snippetFile(parent, lang, fileName, attributes);
                if (!file.exists()) {
                    System.out.println("!!!! WARNING: NO FILE FOUND MATCHING TARGET PASSED IN AT PATH : " + file.getPath());
                    continue;
                }

                String indentAttribute = indent == null ? "" : tags == null ? "indent=" + indent : ",indent=" + indent;
                if (tags != null) {
                    List<String> tagIncludes = new ArrayList<>(tags.length);
                    for (String tag : tags) {
                        tagIncludes.add("include::" + file.getAbsolutePath() + "[tag=" + tag + indentAttribute + "]");
                    }
                    includes.add(String.join("\n\n", tagIncludes));
                } else {
                    includes.add("include::" + file.getAbsolutePath() + "[" + indentAttribute + "]");
                }
            }

            if (!includes.isEmpty()) {
                content.append("""
                    [source.multi-language-sample,%s,%s]
                    ----
                    %s
                    ----

                    """.formatted(lang, title, String.join("\n\n", includes)));
            }
        }
        if (!content.isEmpty()) {
            Options options = Options.builder()
                .attributes(
                    Attributes.builder()
                        .attribute("source-highlighter", "highlightjs")
                        .build()
                )
                .safe(SafeMode.UNSAFE)
                .build();
            String result = asciidoctor.convert(content.toString(), options);
            return createBlock(parent, "pass", result);
        }
        return null;
    }
}
