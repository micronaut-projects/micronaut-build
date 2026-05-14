package io.micronaut.docs.macros;

import io.micronaut.docs.DefaultRenderer;
import io.micronaut.docs.Renderer;
import io.micronaut.docs.SnippetSourceResolver;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.BlockMacroProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Asciidoc block macro that renders source snippets for each configured language.
 */
public class LanguageSnippetMacro extends BlockMacroProcessor implements ValueAtAttributes {
    private static final String LANG_JAVA = "java";
    private static final String LANG_GROOVY = "groovy";
    private static final String LANG_KOTLIN = "kotlin";
    private static final String LANG_PYTHON = "python";
    public static final List<String> LANGS = List.of(LANG_JAVA, LANG_PYTHON, LANG_KOTLIN, LANG_GROOVY);

    private final Renderer renderer;

    public LanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor) {
        this(macroName, config, asciidoctor, new DefaultRenderer(asciidoctor));
    }

    /**
     * Creates a language snippet macro with a custom renderer.
     *
     * @param macroName The macro name.
     * @param config The macro configuration.
     * @param asciidoctor The Asciidoctor instance used by the default renderer.
     * @param renderer The renderer to use.
     */
    public LanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor, Renderer renderer) {
        super(macroName, config);
        Renderer resolvedRenderer = renderer == null ? new DefaultRenderer(asciidoctor) : renderer;
        if (resolvedRenderer instanceof DefaultRenderer defaultRenderer) {
            defaultRenderer.setAsciidoctor(asciidoctor);
        }
        this.renderer = resolvedRenderer;
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
        List<Renderer.CodeSample> samples = new ArrayList<>();

        Object defaultLanguageAttribute = parent.getDocument().getAttribute("default-language");
        String defaultLanguage = defaultLanguageAttribute == null ? null : defaultLanguageAttribute.toString();
        List<String> languagesToRender = SnippetSourceResolver.languagesToRender(defaultLanguage);

        String[] files = target.split(",");
        for (String lang : languagesToRender) {
            StringBuilder content = new StringBuilder();
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
                        String trimmedTag = tag.trim();
                        if (!trimmedTag.isEmpty()) {
                            tagIncludes.add("include::" + file.getAbsolutePath() + "[tag=" + trimmedTag + indentAttribute + "]");
                        }
                    }
                    if (!tagIncludes.isEmpty()) {
                        includes.add(String.join("\n\n", tagIncludes));
                    }
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
                samples.add(new Renderer.CodeSample(lang, content.toString()));
            }
        }
        if (!samples.isEmpty()) {
            return createBlock(parent, "pass", renderer.renderLanguageSnippet(new Renderer.LanguageSnippet(title, samples)));
        }
        return null;
    }
}
