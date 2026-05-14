package io.micronaut.docs

import io.micronaut.docs.macros.LanguageSnippetMacro
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class LanguageSnippetMacroSpec extends Specification {

    @TempDir
    Path testDirectory

    private Asciidoctor asciidoctor

    def setup() {
        asciidoctor = Asciidoctor.Factory.create()
        asciidoctor.javaExtensionRegistry().blockMacro(new LanguageSnippetMacro("snippet", [:], asciidoctor))
    }

    def cleanup() {
        asciidoctor.shutdown()
    }

    void "renders tagged snippets for all default languages"() {
        given:
        file("test-suite/src/test/java/example/Foo.java", '''
            // tag::body[]
            class Foo {}
            // end::body[]
        ''')
        file("test-suite-python/src/test/python/example/Foo.py", '''
            # tag::body[]
            class Foo:
                pass
            # end::body[]
        ''')
        file("test-suite-kotlin/src/test/kotlin/example/Foo.kt", '''
            // tag::body[]
            class Foo
            // end::body[]
        ''')
        file("test-suite-groovy/src/test/groovy/example/Foo.groovy", '''
            // tag::body[]
            class Foo {}
            // end::body[]
        ''')

        when:
        String converted = convert('snippet::example.Foo[tags=body,indent=0,title="Example"]')

        then:
        converted == '''<div class="listingblock multi-language-sample">
<div class="title">Example</div>
<div class="content">
<pre class="highlightjs highlight"><code class="language-java hljs" data-lang="java">class Foo {}</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="title">Example</div>
<div class="content">
<pre class="highlightjs highlight"><code class="language-python hljs" data-lang="python">class Foo:
    pass</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="title">Example</div>
<div class="content">
<pre class="highlightjs highlight"><code class="language-kotlin hljs" data-lang="kotlin">class Foo</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="title">Example</div>
<div class="content">
<pre class="highlightjs highlight"><code class="language-groovy hljs" data-lang="groovy">class Foo {}</code></pre>
</div>
</div>'''
    }

    void "renders only the default language when the document sets one"() {
        given:
        file("test-suite/src/test/java/example/Foo.java", '''
            // tag::body[]
            class Foo {}
            // end::body[]
        ''')
        file("test-suite-kotlin/src/test/kotlin/example/Foo.kt", '''
            // tag::body[]
            class Foo
            // end::body[]
        ''')

        when:
        String converted = convert('snippet::example.Foo[tags=body,indent=0]', ['default-language': 'kotlin'])

        then:
        converted == '''<div class="listingblock multi-language-sample">
<div class="content">
<pre class="highlightjs highlight"><code class="language-kotlin hljs" data-lang="kotlin">class Foo</code></pre>
</div>
</div>'''
    }

    private String convert(String input, Map<String, Object> attributes = [:]) {
        def attributesBuilder = Attributes.builder()
                .attribute('source-highlighter', 'highlightjs')
                .attribute('sourcedir', testDirectory.toString())
        attributes.each { key, value -> attributesBuilder.attribute(key, value) }
        def options = Options.builder()
                .safe(SafeMode.SAFE)
                .attributes(attributesBuilder.build())
                .backend("html5")
                .build()
        asciidoctor.convert(input, options)
    }

    private File file(String path, String content) {
        Path file = testDirectory.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content.stripIndent().trim() + System.lineSeparator())
        file.toFile()
    }
}
