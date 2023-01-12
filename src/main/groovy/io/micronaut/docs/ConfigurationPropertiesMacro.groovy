package io.micronaut.docs

import groovy.transform.CompileStatic
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.ast.Block
import org.asciidoctor.ast.ContentModel
import org.asciidoctor.ast.StructuralNode
import org.asciidoctor.extension.BlockProcessor
import org.asciidoctor.extension.Contexts
import org.asciidoctor.extension.Name
import org.asciidoctor.extension.Reader

@CompileStatic
@Name("configuration")
@Contexts([Contexts.LISTING])
@ContentModel(ContentModel.COMPOUND)
class ConfigurationPropertiesMacro extends BlockProcessor {

    private final Asciidoctor asciidoctor

    ConfigurationPropertiesMacro(Asciidoctor asciidoctor) {
        this.asciidoctor = asciidoctor
    }

    @Override
    Object process(StructuralNode parent, Reader reader, Map<String, Object> attributes) {
        String content = reader.read()
        String title = attributes["title"]
        TomlFormatConverter converter = new TomlFormatConverter(content)
        Block compound = createBlock(parent, "open", "", attributes)
        Block yaml = createBlock(compound, "pass", toLanguageSample(converter.toYaml(), 'yaml', title))
        compound.append(yaml)
        Block toml = createBlock(compound, "pass", toLanguageSample(content, 'toml', title))
        compound.append(toml)
        Block hocon = createBlock(compound, "pass", toLanguageSample(converter.toHocon(), 'hocon', title))
        compound.append(hocon)
        Block javaProperties = createBlock(compound, "pass", toLanguageSample(converter.toJavaProperties(), 'properties', title))
        compound.append(javaProperties)
        compound
    }

    private String toLanguageSample(String sample, String language, String title) {
        def options = Options.builder()
            .attributes(
                Attributes.builder()
                        .attribute('source-highlighter', 'highlightjs')
                        .build()
        ).build()
        String maybeTitle = title?",$title":""
        asciidoctor.convert("""
[source.multi-language-sample,$language${maybeTitle}]
----
$sample
----
""", options)
    }

}
