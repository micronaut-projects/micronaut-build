package io.micronaut.docs;

import io.micronaut.docs.asciidoc.AsciiDocEngine;
import org.junit.jupiter.api.Test;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsciiDocEngineTest {

    @Test
    void exposesDefaultAttributes() {
        var engine = newEngine();

        assertEquals("../img", engine.getAttributes().get("imagesdir"));
        assertEquals("coderay", engine.getAttributes().get("source-highlighter"));
        assertEquals("font", engine.getAttributes().get("icons"));
    }

    @Test
    void rendersImagesFromDefaultImagesDirectory() {
        assertHtmlEquals("""
            <div class="imageblock">
            <div class="content">
            <img src="../img/logo.png" alt="logo">
            </div>
            </div>
            """, render("image::logo.png[]"));
    }

    @Test
    void rendersAdmonitionsWithDefaultFontIcons() {
        assertHtmlEquals("""
            <div class="admonitionblock tip">
            <table>
            <tr>
            <td class="icon">
            <i class="fa icon-tip" title="Tip"></i>
            </td>
            <td class="content">
            Keep going.
            </td>
            </tr>
            </table>
            </div>
            """, render("TIP: Keep going."));
    }

    @Test
    void rendersSourceBlocksWithDefaultCoderayHighlighter() {
        assertHtmlEquals("""
            <div class="listingblock">
            <div class="content">
            <pre class="CodeRay highlight"><code data-lang="java"><span class="type">class</span> <span class="class">Foo</span> {}</code></pre>
            </div>
            </div>
            """, render("""
            [source,java]
            ----
            class Foo {}
            ----
            """));
    }

    private static String render(String input) {
        var engine = newEngine();
        var renderContext = new BaseRenderContext();
        renderContext.setRenderEngine(engine);
        return engine.render(input, renderContext);
    }

    private static AsciiDocEngine newEngine() {
        var initialContext = new BaseInitialRenderContext();
        var engine = new AsciiDocEngine(initialContext);
        initialContext.setRenderEngine(engine);
        return engine;
    }

    private static void assertHtmlEquals(String expected, String actual) {
        assertEquals(expected.stripTrailing(), actual.stripTrailing());
    }
}
