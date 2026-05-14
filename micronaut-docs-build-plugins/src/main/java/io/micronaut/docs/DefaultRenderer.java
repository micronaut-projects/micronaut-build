package io.micronaut.docs;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;

/**
 * Default HTML renderer for the built-in documentation templates.
 */
public class DefaultRenderer implements Renderer {

    /**
     * Shared default renderer instance.
     */
    public static final Renderer INSTANCE = new DefaultRenderer();
    private Asciidoctor asciidoctor;

    /**
     * Creates a renderer without an Asciidoctor instance.
     */
    public DefaultRenderer() {
    }

    /**
     * Creates a renderer that can render Asciidoc-backed snippets.
     *
     * @param asciidoctor The Asciidoctor instance to use for snippets.
     */
    public DefaultRenderer(Asciidoctor asciidoctor) {
        this.asciidoctor = asciidoctor;
    }

    /**
     * Sets the Asciidoctor instance used to render language snippets.
     *
     * @param asciidoctor The Asciidoctor instance.
     */
    public void setAsciidoctor(Asciidoctor asciidoctor) {
        this.asciidoctor = asciidoctor;
    }

    @Override
    public String renderBuildDependency(BuildDependency dependency) {
        return renderGradleDependency(new Dependency(
            "gradle",
            dependency.groupId(),
            dependency.artifactId(),
            dependency.version(),
            dependency.classifier(),
            dependency.gradleScope(),
            dependency.multilanguageCssClass(),
            dependency.title()
        )) + renderMavenDependency(new Dependency(
            "maven",
            dependency.groupId(),
            dependency.artifactId(),
            dependency.version(),
            dependency.classifier(),
            dependency.mavenScope(),
            dependency.multilanguageCssClass(),
            dependency.title()
        ));
    }

    @Override
    public String renderGradleDependency(Dependency dependency) {
        StringBuilder html = new StringBuilder("""
            <div class="listingblock %s">
            <div class="title">%s</div>
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-kotlin hljs" data-lang="%s">\
            """.formatted(dependency.multilanguageCssClass(), dependency.title(), dependency.build()));

        html.append(dependency.scope())
            .append("(<span class=\"hljs-string\">\"")
            .append(dependency.groupId())
            .append(":")
            .append(dependency.artifactId());
        if (dependency.version() != null || dependency.classifier() != null) {
            html.append(":");
        }
        if (dependency.version() != null) {
            html.append(dependency.version());
        }
        if (dependency.classifier() != null) {
            html.append(":").append(dependency.classifier());
        }
        html.append("\")</span>");

        html.append("""
            </code></pre>
            </div>
            </div>
            """);
        return html.toString();
    }

    @Override
    public String renderMavenDependency(Dependency dependency) {
        StringBuilder html = new StringBuilder();
        if ("annotationProcessor".equals(dependency.scope())) {
            html.append("""
                <div class="listingblock %s">
                <div class="title">%s</div>
                <div class="content">
                <pre class="highlightjs highlight"><code class="language-xml hljs" data-lang="%s">&lt;annotationProcessorPaths&gt;
                    &lt;path&gt;
                        &lt;groupId&gt;%s&lt;/groupId&gt;
                        &lt;artifactId&gt;%s&lt;/artifactId&gt;\
                """.formatted(
                dependency.multilanguageCssClass(),
                dependency.title(),
                dependency.build(),
                dependency.groupId(),
                dependency.artifactId()
            ));
            if (dependency.version() != null) {
                html.append("\n        &lt;version&gt;").append(dependency.version()).append("&lt;/version&gt;");
            }
            if (dependency.classifier() != null) {
                html.append("\n        &lt;classifier&gt;").append(dependency.classifier()).append("&lt;/classifier&gt;");
            }
            html.append("""

                    &lt;/path&gt;
                &lt;/annotationProcessorPaths&gt;</code></pre>
                </div>
                </div>
                """);
        } else {
            html.append("""
                <div class="listingblock %s">
                <div class="content">
                <pre class="highlightjs highlight"><code class="language-xml hljs" data-lang="%s">&lt;dependency&gt;
                    &lt;groupId&gt;%s&lt;/groupId&gt;
                    &lt;artifactId&gt;%s&lt;/artifactId&gt;\
                """.formatted(
                dependency.multilanguageCssClass(),
                dependency.build(),
                dependency.groupId(),
                dependency.artifactId()
            ));
            if (dependency.version() != null) {
                html.append("\n    &lt;version&gt;").append(dependency.version()).append("&lt;/version&gt;");
            }
            if (!"compile".equals(dependency.scope())) {
                html.append("\n    &lt;scope&gt;").append(dependency.scope()).append("&lt;/scope&gt;");
            }
            if (dependency.classifier() != null) {
                html.append("\n    &lt;classifier&gt;").append(dependency.classifier()).append("&lt;/classifier&gt;");
            }
            html.append("""

                &lt;/dependency&gt;</code></pre>
                </div>
                </div>
                """);
        }
        return html.toString();
    }

    @Override
    public String renderConfigurationProperties(ConfigurationProperties configurationProperties) {
        return renderCodeSamples(configurationProperties.title(), configurationProperties.samples());
    }

    @Override
    public String renderLanguageSnippet(LanguageSnippet languageSnippet) {
        if (asciidoctor == null) {
            throw new IllegalStateException("Asciidoctor must be configured to render language snippets");
        }
        StringBuilder content = new StringBuilder();
        for (CodeSample sample : languageSnippet.samples()) {
            content.append(sample.source());
        }
        Options options = Options.builder()
            .attributes(
                Attributes.builder()
                    .attribute("source-highlighter", "highlightjs")
                    .build()
            )
            .safe(SafeMode.UNSAFE)
            .build();
        return asciidoctor.convert(content.toString(), options);
    }

    @Override
    public String renderNote(String content) {
        return "<blockquote class=\"note\">" + content + "</blockquote>";
    }

    @Override
    public String renderWarning(String content) {
        return "<blockquote class=\"warning\">" + content + "</blockquote>";
    }

    @Override
    public String renderHidden(String content) {
        return "<div class=\"hidden-block\">" + content + "</div>";
    }

    @Override
    public String renderHeader(String level, String content) {
        return "<h" + level + ">" + content + "</h" + level + ">";
    }

    @Override
    public String renderBlockQuote(String content) {
        return "<pre class=\"bq\"><code>" + content + "</code></pre>\n\n";
    }

    @Override
    public String renderBold(String content) {
        return "<strong class=\"bold\">" + content + "</strong>";
    }

    @Override
    public String renderCode(String content) {
        if (content.contains("class=\"code\"")) {
            return "@" + content + "@";
        }
        return "<code>" + content + "</code>";
    }

    @Override
    public String renderItalic(String content) {
        return " <em class=\"italic\">" + content + "</em> ";
    }

    @Override
    public String renderImage(String src) {
        return "<img border=\"0\" class=\"center\" src=\"" + src + "\"></img>";
    }

    @Override
    public String renderLink(String href, String text) {
        return "<a href=\"" + href + "\">" + text + "</a>";
    }

    @Override
    public String renderLink(String href, String text, String cssClass) {
        return "<a href=\"" + href + "\" class=\"" + cssClass + "\">" + text + "</a>";
    }

    @Override
    public String renderExternalLink(String href, String text) {
        return "<a href=\"" + href + "\" target=\"blank\">" + text + "</a>";
    }

    @Override
    public String renderRedirect(String target) {
        return """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
            <html lang="en">
            <head>
            <meta http-equiv="refresh" content="0; url=%s" />
            </head>

            </body>
            </html>
            """.formatted(target);
    }

    @Override
    public String renderTocTree(TocTree tocTree) {
        StringBuilder html = new StringBuilder();
        for (TocNode child : tocTree.children()) {
            renderTocNode(html, child);
        }
        return html.toString();
    }

    @Override
    public String renderGuideSummary(GuideSummary guideSummary) {
        StringBuilder html = new StringBuilder();
        for (GuideSummaryItem item : guideSummary.items()) {
            html.append("""
                                            <div class="toc-item" style="margin-left:0"><a href="%s/guide/%s.html"><strong>%s</strong><span>%s</span></a>
                                            </div>
                """.formatted(item.path(), item.page(), item.number(), item.title()));
        }
        return html.toString();
    }

    @Override
    public String renderSectionToc(SectionToc sectionToc) {
        if (sectionToc.items().isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("""
                                    <div id="table-of-content">
                                        <h2>Table of Contents</h2>
            """);
        for (SectionTocItem item : sectionToc.items()) {
            html.append("""
                                        <div class="toc-item" style="margin-left:%spx"><a href="#%s"><strong>%s</strong><span>%s</span></a>
                                        </div>
                """.formatted(item.marginLeft(), item.href(), item.number(), item.title()));
        }
        html.append("                </div>\n");
        return html.toString();
    }

    @Override
    public String renderReferenceMenu(ReferenceMenu referenceMenu) {
        StringBuilder html = new StringBuilder();
        for (ReferenceMenuCategory category : referenceMenu.categories()) {
            html.append("""
                                    <div class="menu-block">
                                        <h1 class="menu-title" onclick="toggleRef(nextElement(this))">%s</h1>
                                        <div class="menu-sub%s">
                """.formatted(category.title(), category.selected() ? " selected" : ""));
            if (category.hasUsage()) {
                html.append("""
                                                <div class="menu-item"><a href="%s/ref/%s/Usage.html">Usage</a></div>
                    """.formatted(category.path(), category.categoryPath()));
            }
            for (ReferenceMenuItem item : category.items()) {
                html.append("""
                                                <div class="menu-item"><a href="%s/ref/%s/%s.html">%s</a>
                                                </div>
                    """.formatted(category.path(), category.categoryPath(), item.sectionPath(), item.title()));
            }
            html.append("""
                                        </div>
                                    </div>
                """);
        }
        return html.toString();
    }

    private static String renderCodeSamples(String title, Iterable<CodeSample> samples) {
        StringBuilder html = new StringBuilder();
        for (CodeSample sample : samples) {
            html.append("""
                <div class="listingblock multi-language-sample">
                """);
            if (title != null) {
                html.append("<div class=\"title\">").append(title).append("</div>\n");
            }
            html.append("""
                <div class="content">
                <pre class="highlightjs highlight"><code class="language-%s hljs" data-lang="%s">%s</code></pre>
                </div>
                </div>
                """.formatted(sample.language(), sample.language(), html(sample.source().stripTrailing())));
        }
        return html.toString();
    }

    private static void renderTocNode(StringBuilder html, TocNode node) {
        if (node.children().isEmpty()) {
            html.append("""
                <div class="toc-item" id="toc-item-%s">
                    <a class="toc-link" href="%s" data-section="%s"><span class="toc-number">%s</span><span class="toc-title">%s</span></a>
                </div>
                """.formatted(node.id(), node.href(), node.dataSection(), node.number(), node.title()));
            return;
        }
        html.append("""
            <details class="toc-section" id="toc-item-%s" open>
                <summary><a href="%s" data-section="%s"><span class="toc-number">%s</span><span class="toc-title">%s</span></a></summary>
                <div class="toc-children">
            """.formatted(node.id(), node.href(), node.dataSection(), node.number(), node.title()));
        for (TocNode child : node.children()) {
            renderTocNode(html, child);
        }
        html.append("""
                </div>
            </details>
            """);
    }

    private static String html(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
