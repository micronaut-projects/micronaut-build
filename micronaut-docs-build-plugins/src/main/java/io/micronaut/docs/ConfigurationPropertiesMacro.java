package io.micronaut.docs;

import io.micronaut.docs.converter.YamlFormatConverter;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.ContentModel;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.BlockProcessor;
import org.asciidoctor.extension.Contexts;
import org.asciidoctor.extension.Name;
import org.asciidoctor.extension.Reader;

import java.util.Map;

@Name("configuration")
@Contexts(Contexts.LISTING)
@ContentModel(ContentModel.COMPOUND)
public class ConfigurationPropertiesMacro extends BlockProcessor {

    private final Asciidoctor asciidoctor;

    public ConfigurationPropertiesMacro(Asciidoctor asciidoctor) {
        this.asciidoctor = asciidoctor;
    }

    @Override
    public Object process(StructuralNode parent, Reader reader, Map<String, Object> attributes) {
        String content = reader.read();
        String title = (String) attributes.get("title");
        YamlFormatConverter converter = new YamlFormatConverter(content);
        Block compound = createBlock(parent, "open", "", attributes);
        compound.append(createBlock(compound, "pass", toLanguageSample(converter.toJavaProperties(), "properties", title)));
        compound.append(createBlock(compound, "pass", toLanguageSample(content, "yaml", title)));
        compound.append(createBlock(compound, "pass", toLanguageSample(converter.toToml(), "toml", title)));
        compound.append(createBlock(compound, "pass", toLanguageSample(converter.toGroovy(), "groovy-config", title)));
        compound.append(createBlock(compound, "pass", toLanguageSample(converter.toHocon(), "hocon", title)));
        compound.append(createBlock(compound, "pass", toLanguageSample(converter.toJson(), "json-config", title)));
        return compound;
    }

    private String toLanguageSample(String sample, String language, String title) {
        Options options = Options.builder()
            .attributes(
                Attributes.builder()
                    .attribute("source-highlighter", "highlightjs")
                    .build()
            ).build();
        String maybeTitle = title == null ? "" : "," + title;
        return asciidoctor.convert("""
            [source.multi-language-sample,%s%s]
            ----
            %s
            ----
            """.formatted(language, maybeTitle, sample), options);
    }
}
