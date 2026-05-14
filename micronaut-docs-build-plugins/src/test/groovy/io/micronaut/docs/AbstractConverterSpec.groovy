package io.micronaut.docs

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import spock.lang.Specification

class AbstractConverterSpec extends Specification {
    private Asciidoctor asciidoctor
    protected Options options
    protected String converted

    def setup() {
        asciidoctor = Asciidoctor.Factory.create()
        asciidoctor.javaExtensionRegistry().block(new ConfigurationPropertiesMacro(asciidoctor))
        options = Options.builder()
                .safe(SafeMode.SAFE)
                .attributes(
                        Attributes.builder()
                                .attribute('source-highlighter', 'highlightjs')
                                .build()
                )
                .backend("html5")
                .build()
    }

    def cleanup() {
        asciidoctor.shutdown()
    }


    void convert(String input) {
        converted = asciidoctor.convert(input, options)
    }
}
