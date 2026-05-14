package docs.layout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocsLayoutGeneratedOutputTest {

    @Test
    void generatedDocsRenderFullSampleGuideWithTocMacrosSnippetsAndConfigurationReference() throws IOException {
        String guideHtml = guideHtml();

        assertContains(guideHtml, "<body class=\"body docs-shell\" id=\"docs\"");
        assertNoTemplateMarkers(guideHtml);
        assertContains(guideHtml, "<script src=\"../js/multi-language-sample.js\"></script>");
        assertContains(guideHtml, "<link rel=\"stylesheet\" href=\"../css/multi-language-sample.css\" />");
        assertContains(guideHtml, "<div class=\"app-shell\">");
        assertContains(guideHtml, "class=\"sidebar no-print\"");
        assertContains(guideHtml, "class=\"topbar no-print\"");
        assertContains(guideHtml, "id=\"theme-switcher\" class=\"theme-toggle\"");
        assertContains(guideHtml, "class=\"sidebar-collapse desktop\"");
        assertContains(guideHtml, "class=\"sidebar-toggle mobile\"");
        assertContains(guideHtml, "<article class=\"docs-content guide-document\">");
        assertContains(guideHtml, "href=\"../api/index.html\">API</a>");
        assertContains(guideHtml, "href=\"../guide/configurationreference.html\">Configuration</a>");
        assertTrue(!guideHtml.contains("../css/main.css"));
        assertTrue(!guideHtml.contains("../css/pdf.css"));
        assertTrue(!guideHtml.contains("../css/highlight/"));
        assertTrue(!guideHtml.contains("clipboard.min.js"));

        assertContains(guideHtml, "<span class=\"toc-number\">1</span><span class=\"toc-title\">Introduction</span>");
        assertContains(guideHtml, "<details class=\"toc-section\" id=\"toc-item-setup\" open>");
        assertContains(guideHtml, "<span class=\"toc-number\">2</span><span class=\"toc-title\">Setup</span>");
        assertContains(guideHtml, "<div class=\"toc-children\">");
        assertContains(guideHtml, "<span class=\"toc-number\">2.1</span><span class=\"toc-title\">Requirements</span>");
        assertContains(guideHtml, "<span class=\"toc-number\">2.2</span><span class=\"toc-title\">Modules</span>");
        assertContains(guideHtml, "<span class=\"toc-number\">10</span><span class=\"toc-title\">Deployment</span>");
        assertContains(guideHtml, "<span class=\"toc-number\">10.2</span><span class=\"toc-title\">Native Image</span>");
        assertContains(guideHtml, "<h1 id=\"deployment\"><a class=\"anchor\" href=\"#deployment\"></a>10 Deployment</h1>");
        assertContains(guideHtml, "<h2 id=\"nativeImage\"><a class=\"anchor\" href=\"#nativeImage\"></a>10.2 Native Image</h2>");

        assertContains(guideHtml, "<a href=\"https://docs.micronaut.io/latest/guide/\">Micronaut user guide</a>");
        assertContains(guideHtml, "<div class=\"admonitionblock tip\">");
        assertContains(guideHtml, "<div class=\"admonitionblock note\">");
        assertContains(guideHtml, "<div class=\"admonitionblock warning\">");
        assertContains(guideHtml, "<div class=\"admonitionblock important\">");
        assertContains(guideHtml, "Warning blocks use the same generated docs layout as notes.");
        assertContains(guideHtml, "Important blocks appear in Micronaut core docs and must keep platform styling.");
        assertContains(guideHtml, "<table class=\"tableblock frame-all grid-all stretch\">");
        assertContains(guideHtml, "<caption class=\"title\">Table 1. Coverage table</caption>");
        assertContains(guideHtml, "<div class=\"openblock\">");
        assertContains(guideHtml, "<div class=\"title\">Open block title</div>");
        assertContains(guideHtml, "Open block titles use the same generated title style as listing blocks.");
        assertContains(guideHtml, "<h2 id=\"_code_pointer_examples\">Code Pointer Examples</h2>");
        assertContains(guideHtml, "<h3 id=\"_section\">Section</h3>");
        assertContains(guideHtml, "<h4 id=\"_deep_section\">Deep Section</h4>");
        assertContains(guideHtml, "<h5 id=\"_detail_section\">Detail Section</h5>");
        assertContains(guideHtml, "This fourth-level heading mirrors Micronaut Core appendix and configuration pages.");
        assertContains(guideHtml, "This fifth-level heading mirrors fine-grained Micronaut Core migration notes.");
        assertContains(guideHtml, "<div class=\"title\">Code pointer example</div>");
        assertContains(guideHtml, "class PointerExample");
        assertContains(guideHtml, "<i class=\"conum\" data-value=\"1\"></i><b>(1)</b>");
        assertContains(guideHtml, "<div class=\"colist arabic\">");
        assertContains(guideHtml, "<td><i class=\"conum\" data-value=\"1\"></i><b>1</b></td>");
        assertContains(guideHtml, "Declares the example type used by the docs fixture.");
        assertContains(guideHtml, "Returns the sample value shown in the code block.");
        assertContains(guideHtml, "<div class=\"title\">Block admonition title</div>");
        assertContains(guideHtml, "Micronaut Core docs also use block-form admonitions with titles.");
        assertContains(guideHtml, "<div class=\"ulist\">");
        assertContains(guideHtml, "<div class=\"title\">List coverage</div>");
        assertContains(guideHtml, "<p>First unordered item</p>");
        assertContains(guideHtml, "<div class=\"olist arabic\">");
        assertContains(guideHtml, "<div class=\"title\">Ordered coverage</div>");
        assertContains(guideHtml, "<p>First ordered item</p>");
        assertContains(guideHtml, "<div class=\"quoteblock\">");
        assertContains(guideHtml, "<div class=\"title\">Quote coverage</div>");
        assertContains(guideHtml, "Quote blocks appear in longer guide pages and should keep readable spacing.");
        assertContains(guideHtml, "&#8212; Micronaut Docs");
        assertContains(guideHtml, "<div class=\"literalblock\">");
        assertContains(guideHtml, "<div class=\"title\">Literal coverage</div>");
        assertContains(guideHtml, "<pre>literal block content</pre>");
        assertContains(guideHtml, "<img src=\"../img/micronaut-logo-white.svg\" alt=\"Micronaut Logo\" width=\"120\" height=\"40\">");
        assertContains(guideHtml, "<div class=\"title\">Figure 1. Image coverage</div>");

        assertContains(guideHtml, "<div class=\"listingblock multi-language-sample\">");
        assertContains(guideHtml, "<div class=\"title\">Tagged sample</div>");
        assertContains(guideHtml, "class=\"language-java hljs\" data-lang=\"java\"");
        assertContains(guideHtml, "return \"java\";");
        assertContains(guideHtml, "return \"java-bar\";");
        assertContains(guideHtml, "class=\"language-python hljs\" data-lang=\"python\"");
        assertContains(guideHtml, "return \"python\"");
        assertContains(guideHtml, "return \"python-bar\"");
        assertContains(guideHtml, "class=\"language-kotlin hljs\" data-lang=\"kotlin\"");
        assertContains(guideHtml, "return \"kotlin\"");
        assertContains(guideHtml, "return \"kotlin-bar\"");
        assertContains(guideHtml, "class=\"language-groovy hljs\" data-lang=\"groovy\"");
        assertContains(guideHtml, "return \"groovy\"");
        assertContains(guideHtml, "return \"groovy-bar\"");

        assertContains(guideHtml, "class=\"language-properties hljs\" data-lang=\"properties\"");
        assertContains(guideHtml, "micronaut.server.port=8080");
        assertContains(guideHtml, "micronaut.server.port=8081");
        assertContains(guideHtml, "class=\"language-yaml hljs\" data-lang=\"yaml\"");
        assertContains(guideHtml, "class=\"language-toml hljs\" data-lang=\"toml\"");
        assertContains(guideHtml, "class=\"language-groovy-config hljs\" data-lang=\"groovy-config\"");
        assertContains(guideHtml, "class=\"language-hocon hljs\" data-lang=\"hocon\"");
        assertContains(guideHtml, "class=\"language-json-config hljs\" data-lang=\"json-config\"");

        assertContains(guideHtml, "data-lang=\"gradle\">implementation(<span class=\"hljs-string\">\"io.micronaut:micronaut-runtime\")");
        assertContains(guideHtml, "&lt;artifactId&gt;micronaut-runtime&lt;/artifactId&gt;");
        assertContains(guideHtml, "data-lang=\"gradle\">testImplementation(<span class=\"hljs-string\">\"io.micronaut.test:micronaut-test-junit5:4.0.0:tests\")");
        assertContains(guideHtml, "&lt;scope&gt;test&lt;/scope&gt;");
        assertContains(guideHtml, "&lt;classifier&gt;tests&lt;/classifier&gt;");
        assertContains(guideHtml, "&lt;annotationProcessorPaths&gt;");
        assertContains(guideHtml, "&lt;artifactId&gt;micronaut-inject-java&lt;/artifactId&gt;");
        assertContains(guideHtml, "data-lang=\"gradle\">runtimeOnly(<span class=\"hljs-string\">\"io.micronaut.data:micronaut-data-runtime:4.0.0\")");
        assertContains(guideHtml, "data-lang=\"gradle\">compileOnly(<span class=\"hljs-string\">\"com.example:docs-layout-runtime:1.0.0\")");
        assertContains(guideHtml, "&lt;scope&gt;provided&lt;/scope&gt;");
        assertContains(guideHtml, "data-lang=\"gradle\">implementation(<span class=\"hljs-string\">\"io.micronaut.serde:micronaut-serde-jackson:2.0.0\")");

        assertContains(guideHtml, "<a href=\"../api/io/micronaut/context/ApplicationContext.html\">ApplicationContext</a>");
        assertContains(guideHtml, "<a href=\"https://docs.micronaut.io/latest/api/io/micronaut/context/ApplicationContext.html\">ApplicationContext</a>");
        assertContains(guideHtml, "<a href=\"../api/io/micronaut/context/annotation/Requires.html\">@Requires</a>");
        assertContains(guideHtml, "<a href=\"../api/io/micronaut/context/annotation/package-summary.html\">io.micronaut.context.annotation</a>");
        assertContains(guideHtml, "<a href=\"https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html\">CompletableFuture</a>");
        assertContains(guideHtml, "<a href=\"https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/logging/ConsoleHandler.html\">ConsoleHandler</a>");
        assertContains(guideHtml, "<a href=\"https://docs.oracle.com/javaee/6/api/java.base/javax/inject/Inject.html\">Inject</a>");
        assertContains(guideHtml, "<a href=\"https://www.reactive-streams.org/reactive-streams-1.0.3-javadoc/org/reactivestreams/Publisher.html\">Publisher</a>");
        assertContains(guideHtml, "<a href=\"http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Single.html\">Single</a>");
        assertContains(guideHtml, "<a href=\"https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html\">Flux</a>");
        assertContains(guideHtml, "<a href=\"../api/io/micronaut/http/HttpRequest.html#GET-java.lang.String-\">HttpRequest.GET(java.lang.String)</a>");
        assertContains(guideHtml, "<a href=\"../api/io/micronaut/context/env/Environment.html#TEST\">TEST</a>");
        assertContains(guideHtml, "<a href=\"../api/io/micronaut/context/ApplicationContext.html\">Micronaut context</a>");

        assertTrue(Files.isRegularFile(file("docs.apiIndex")));
        String configReferenceHtml = configReferenceHtml();
        assertNoTemplateMarkers(configReferenceHtml);
        assertContains(configReferenceHtml, "<title>Configuration Reference | Micronaut</title>");
        assertContains(configReferenceHtml, "<body class=\"body docs-shell\" id=\"docs\"");
        assertContains(configReferenceHtml, "<article class=\"docs-content guide-document\">");
        assertContains(configReferenceHtml, "href=\"index.html\">Docs</a>");
        assertContains(configReferenceHtml, "href=\"../api/index.html\">API</a>");
        assertContains(configReferenceHtml, "class=\"toc-link active\" title=\"Go to Configuration Reference documentation\" href=\"configurationreference.html\"");
        assertContains(configReferenceHtml, "<span class=\"toc-number\">03</span><span class=\"toc-title\">Configuration Reference</span>");
        assertTrue(!configReferenceHtml.contains("../css/main.css"));
        assertTrue(!configReferenceHtml.contains("../css/pdf.css"));
        assertTrue(!configReferenceHtml.contains("../css/highlight/"));
        assertContains(configReferenceHtml, "<h1>Configuration Reference</h1>");
        assertContains(configReferenceHtml, "<table class=\"tableblock frame-all grid-all stretch\">");
        assertContains(configReferenceHtml, "<code>normal.config</code>");
        assertContains(configReferenceHtml, "<code>generic.config</code>");

        Path docsRoot = docsRoot();
        assertTrue(!Files.exists(docsRoot.resolve("css/main.css")));
        assertTrue(!Files.exists(docsRoot.resolve("css/pdf.css")));
        assertTrue(!Files.exists(docsRoot.resolve("css/highlight")));
        assertTrue(!Files.exists(docsRoot.resolve("fonts")));
        assertTrue(!Files.exists(docsRoot.resolve("style")));
        assertTrue(!Files.exists(docsRoot.resolve("ref")));

        String customCss = Files.readString(docsRoot.resolve("css/custom.css"));
        assertContains(customCss, "body.docs-shell .toc-children");
        assertContains(customCss, "border-left: 1px solid var(--border);");
        assertContains(customCss, "body.docs-shell .colist");
        assertContains(customCss, "body.docs-shell .snippet-title");
        assertContains(customCss, "body.docs-shell .openblock > .title");
        assertContains(customCss, "body.docs-shell table.tableblock > .title");
        assertContains(customCss, "body.docs-shell table.tableblock > caption.title");
        assertContains(customCss, "text-rendering: optimizeLegibility;");
        assertContains(customCss, "max-width: 0;");
        assertContains(customCss, "--snippet-title: #7a2518;");
        assertContains(customCss, "--code-background: #333333;");
        assertContains(customCss, "--table-border: #dedede;");
        assertContains(customCss, "body.docs-shell .admonitionblock.warning");
        assertContains(customCss, "body.docs-shell .admonitionblock.important");
        assertContains(customCss, "body.docs-shell .admonitionblock td.icon .icon-tip::before");
        assertContains(customCss, "body.docs-shell .admonitionblock td.icon .icon-tip::after");
        assertContains(customCss, "background: #ffd35a;");
        assertContains(customCss, "color: #7a5600;");
        assertContains(customCss, "body.docs-shell .admonitionblock td.icon .icon-warning::after");
        assertContains(customCss, "border-bottom: 26px solid #f1c102;");
        assertContains(customCss, "body.docs-shell .admonitionblock td.icon .icon-important::before");
        assertContains(customCss, "content: \"!\";");
        assertTrue(!customCss.contains("content: \"Tip\";"));
        assertTrue(!customCss.contains("content: \"Warn\";"));
        assertTrue(!customCss.contains("content: \"Important\";"));
        assertContains(customCss, "background: #ffffce;");
        assertContains(customCss, "background: #aa0000;");
        assertContains(customCss, "color: rgba(0, 0, 0, 0.6);");
        assertContains(customCss, "display: inline-block;");
        assertContains(customCss, "width: 1.67em;");
        assertContains(customCss, "body.docs-shell pre .conum[data-value]");
        assertContains(customCss, "margin-top: -0.5em;");
        assertContains(customCss, "font-size: 13.6px;");
        assertContains(customCss, "vertical-align: middle;");
        assertContains(customCss, "width: auto;");
        assertContains(customCss, "padding: 0 0.75em;");

        String multiLanguageCss = Files.readString(docsRoot.resolve("css/multi-language-sample.css"));
        assertContains(multiLanguageCss, "body.docs-shell .multi-language-selector");
        assertContains(multiLanguageCss, "margin: 0;");
        assertContains(multiLanguageCss, "body.docs-shell .multi-language-selector .language-option[data-lang='maven']");
        assertContains(multiLanguageCss, "padding-left: 56px;");
        assertContains(multiLanguageCss, "body.docs-shell .multi-language-tab-panel pre");
        assertContains(multiLanguageCss, "background-color: #222222;");

        String multiLanguageJs = Files.readString(docsRoot.resolve("js/multi-language-sample.js"));
        assertContains(multiLanguageJs, "if (currentLanguages[nextLanguage])");
        assertContains(multiLanguageJs, "multi-language-tab-panel");
        assertContains(multiLanguageJs, "snippet-title");
    }

    private static String guideHtml() throws IOException {
        return Files.readString(file("docs.guide"));
    }

    private static String configReferenceHtml() throws IOException {
        return Files.readString(file("docs.configReference"));
    }

    private static Path file(String propertyName) {
        String path = System.getProperty(propertyName);
        assertNotNull(path, () -> "Missing system property: " + propertyName);
        return Path.of(path);
    }

    private static Path docsRoot() {
        return file("docs.guide").getParent().getParent();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected generated output to contain: " + expected);
    }

    private static void assertNoTemplateMarkers(String actual) {
        assertTrue(!actual.contains("<%"));
        assertTrue(!actual.contains("%>"));
        assertTrue(!actual.contains("${"));
        assertTrue(!actual.contains("{{"));
        assertTrue(!actual.contains("}}"));
    }
}
