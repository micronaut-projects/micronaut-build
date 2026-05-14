package io.micronaut.docs.macros;

import io.micronaut.docs.DefaultRenderer;
import io.micronaut.docs.Renderer;
import io.micronaut.docs.converter.YamlFormatConverter;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.ContentModel;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.BlockProcessor;
import org.asciidoctor.extension.Contexts;
import org.asciidoctor.extension.Name;
import org.asciidoctor.extension.Reader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Name("configuration")
@Contexts(Contexts.LISTING)
@ContentModel(ContentModel.COMPOUND)
public class ConfigurationPropertiesMacro extends BlockProcessor {

    private final Renderer renderer;

    public ConfigurationPropertiesMacro(Asciidoctor asciidoctor) {
        this(asciidoctor, DefaultRenderer.INSTANCE);
    }

    /**
     * Creates a configuration properties macro with a custom renderer.
     *
     * @param asciidoctor The Asciidoctor instance.
     * @param renderer The renderer to use.
     */
    public ConfigurationPropertiesMacro(Asciidoctor asciidoctor, Renderer renderer) {
        this.renderer = renderer == null ? DefaultRenderer.INSTANCE : renderer;
    }

    @Override
    public Object process(StructuralNode parent, Reader reader, Map<String, Object> attributes) {
        String content = reader.read();
        String title = (String) attributes.get("title");
        YamlFormatConverter converter = new YamlFormatConverter(content);
        Map<String, Object> blockAttributes = new LinkedHashMap<>(attributes);
        blockAttributes.remove("title");
        Block compound = createBlock(parent, "open", "", blockAttributes);
        compound.append(createBlock(compound, "pass", renderer.renderConfigurationProperties(new Renderer.ConfigurationProperties(
            title,
            List.of(
                sample("properties", converter.toJavaProperties()),
                sample("yaml", content),
                sample("toml", converter.toToml()),
                sample("groovy-config", converter.toGroovy()),
                sample("hocon", converter.toHocon()),
                sample("json-config", converter.toJson())
            )
        ))));
        return compound;
    }

    private static Renderer.CodeSample sample(String language, String source) {
        return new Renderer.CodeSample(language, source.stripTrailing());
    }
}
