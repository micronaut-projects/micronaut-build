package io.micronaut.docs;

import org.junit.jupiter.api.Test;

import java.util.Map;

class DocsFilterTest extends AbstractDocsMacroTest {

    @Test
    void rendersParamFilterForDefinedParameters() {
        assertHtmlEquals(
            "Hello Docs.",
            renderGdoc("Hello {$name}.", Map.of("name", "Docs"))
        );
    }

    @Test
    void rendersParamFilterForMissingParameters() {
        assertHtmlEquals(
            "Hello <name>.",
            renderGdoc("Hello {$name}.")
        );
    }

    @Test
    void rendersDocsMacroFilter() {
        assertHtmlEquals(
            "<blockquote class=\"note\">Pay attention.</blockquote>",
            renderGdoc("{note}Pay attention.{note}")
        );
    }

    @Test
    void rendersHeaderFilter() {
        assertHtmlEquals(
            "<h2>Section Title</h2>",
            renderGdoc("h2. Section Title")
        );
    }

    @Test
    void rendersBlockQuoteFilter() {
        assertHtmlEquals(
            "<pre class=\"bq\"><code> quoted text</code></pre><p class=\"paragraph\"/>",
            renderGdoc("bc. quoted text\n\n")
        );
    }

    @Test
    void rendersLineFilter() {
        assertHtmlEquals(
            "<hr class=\"line\"/>",
            renderGdoc("-----")
        );
    }

    @Test
    void rendersStrikeThroughFilter() {
        assertHtmlEquals(
            "This <strike class=\"strike\">removed</strike> text.",
            renderGdoc("This --removed-- text.")
        );
    }

    @Test
    void rendersNewlineFilter() {
        assertHtmlEquals(
            "Line one<br/>Line two",
            renderGdoc("Line one\\\\Line two")
        );
    }

    @Test
    void rendersParagraphFilter() {
        assertHtmlEquals(
            "First paragraph.<p class=\"paragraph\"/>Second paragraph.",
            renderGdoc("First paragraph.\n\nSecond paragraph.")
        );
    }

    @Test
    void rendersBoldFilter() {
        assertHtmlEquals(
            "Text with <strong class=\"bold\">bold</strong>.",
            renderGdoc("Text with *bold*.")
        );
    }

    @Test
    void rendersItalicFilter() {
        assertHtmlEquals(
            "Text with  <em class=\"italic\">italic</em> .",
            renderGdoc("Text with _italic_.")
        );
    }

    @Test
    void rendersCodeFilter() {
        assertHtmlEquals(
            "Text with <code>code</code>.",
            renderGdoc("Text with @code@.")
        );
    }

    @Test
    void preservesAlreadyMarkedCodeForThePublisherPipeline() {
        assertHtmlEquals(
            "@&#60;span class=\"code\"&#62;literal&#60;/span&#62;@",
            renderGdoc("@<span class=\"code\">literal</span>@")
        );
    }

    @Test
    void rendersTextileLinkFilterForExternalLinks() {
        assertHtmlEquals(
            "<a href=\"https://micronaut.io\" target=\"blank\">Micronaut</a> link.",
            renderGdoc("\"Micronaut\":https://micronaut.io link.")
        );
    }

    @Test
    void rendersTextileLinkFilterForRelativeLinks() {
        assertHtmlEquals(
            "<a href=\"guide/index.html\">Local</a> link.",
            renderGdoc("\"Local\":guide/index.html link.")
        );
    }

    @Test
    void rendersImageFilterForLocalImages() {
        assertHtmlEquals(
            "<img border=\"0\" class=\"center\" src=\"../img/logo.png\"></img>",
            renderGdoc("!logo.png!")
        );
    }

    @Test
    void rendersImageFilterForExternalImages() {
        assertHtmlEquals(
            "<img border=\"0\" class=\"center\" src=\"https://micronaut.io/logo.png\"></img>",
            renderGdoc("!https://micronaut.io/logo.png!")
        );
    }

    @Test
    void rendersMarkFilter() {
        assertHtmlEquals(
            "<a href=\"http://neotis.de/\">neotis&#174;</a>",
            renderGdoc("neotis ")
        );
    }

    @Test
    void rendersKeyFilter() {
        assertHtmlEquals(
            "Press <span class=\"key\">Ctrl-C</span> now.",
            renderGdoc("Press Ctrl-C now.")
        );
    }

    @Test
    void rendersTypographyFilter() {
        assertHtmlEquals(
            "Wait&#8230; done.",
            renderGdoc("Wait... done.")
        );
    }

    @Test
    void rendersEscapeFilter() {
        assertHtmlEquals(
            "&#60;tag attr=\"value\"&#62;&#38;",
            renderGdoc("<tag attr=\"value\">&")
        );
    }

    @Test
    void rendersLinkTestFilterForExternalLinks() {
        assertHtmlEquals(
            "<a href=\"https://micronaut.io/docs\" target=\"blank\">Micronaut</a>",
            renderGdoc("[Micronaut|https://micronaut.io/docs]")
        );
    }

    @Test
    void rendersListFilter() {
        assertHtmlEquals("""
            <ul class="star">
            <li>first item</li>
            <li>second item</li>
            </ul><p class="paragraph"/>
            """, renderGdoc("""
            * first item
            * second item
            """));
    }
}
