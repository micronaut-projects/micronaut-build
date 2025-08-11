package io.micronaut.docs.asciidoc

import groovy.transform.InheritConstructors
import io.micronaut.docs.DocEngine
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.radeox.api.engine.context.InitialRenderContext
import org.radeox.api.engine.context.RenderContext

import static org.asciidoctor.Asciidoctor.Factory.create
/**
 * A DocEngine implementation that uses Asciidoctor to render pages
 *
 * @author Graeme Rocher
 * @since 3.2.0
 */
class AsciiDocEngine extends DocEngine {
    Asciidoctor asciidoctor = create();
    Map attributes = [
        'imagesdir': '../img',
        'source-highlighter':'coderay',
        'icons':'font'
    ]

    AsciiDocEngine(InitialRenderContext context) {
        super(context)
    }

    @Override
    String render(String content, RenderContext context) {
        def optionsBuilder = Options.builder()
                                        .headerFooter(false)
                                        .attributes(Attributes.builder().attributes(attributes).build())
        if(attributes.containsKey('safe')) {
            optionsBuilder.safe(SafeMode.valueOf(attributes.get('safe').toString()))
        }
        asciidoctor.convert(content, optionsBuilder.build())
    }

}
