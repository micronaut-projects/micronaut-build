package io.micronaut.docs.asciidoc;

import io.micronaut.docs.DocEngine;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.asciidoctor.Asciidoctor.Factory.create;

/**
 * A DocEngine implementation that uses Asciidoctor to render pages.
 *
 * @author Graeme Rocher
 * @since 3.2.0
 */
public class AsciiDocEngine extends DocEngine {
    private final Asciidoctor asciidoctor = create();
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public AsciiDocEngine(InitialRenderContext context) {
        super(context);
        attributes.put("imagesdir", "../img");
        attributes.put("source-highlighter", "coderay");
        attributes.put("icons", "font");
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String render(String content, RenderContext context) {
        var optionsBuilder = Options.builder()
            .standalone(false);
        var attrsBuilder = Attributes.builder();
        attributes.forEach((key, value) -> attrsBuilder.attribute(key, value));
        optionsBuilder.attributes(attrsBuilder.build());
        if (attributes.containsKey("safe")) {
            optionsBuilder.safe(SafeMode.valueOf(attributes.get("safe").toString()));
        }
        return asciidoctor.convert(content, optionsBuilder.build());
    }
}
