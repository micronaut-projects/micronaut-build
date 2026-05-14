package io.micronaut.docs;

import java.util.List;

/**
 * Renders the HTML fragments emitted by the documentation engine, filters and macros.
 *
 * <p>The renderer is intentionally separate from parsing so downstream projects can keep the
 * existing macro semantics while replacing the generated HTML.</p>
 */
public interface Renderer {

    /**
     * Renders a dependency macro as the default Gradle and Maven snippets.
     *
     * @param dependency The dependency request.
     * @return The rendered HTML.
     */
    String renderBuildDependency(BuildDependency dependency);

    /**
     * Renders a single Gradle dependency snippet.
     *
     * @param dependency The dependency snippet request.
     * @return The rendered HTML.
     */
    String renderGradleDependency(Dependency dependency);

    /**
     * Renders a single Maven dependency snippet.
     *
     * @param dependency The dependency snippet request.
     * @return The rendered HTML.
     */
    String renderMavenDependency(Dependency dependency);

    /**
     * Renders the converted configuration property samples.
     *
     * @param configurationProperties The configuration samples to render.
     * @return The rendered HTML.
     */
    String renderConfigurationProperties(ConfigurationProperties configurationProperties);

    /**
     * Renders source snippets for one or more languages.
     *
     * @param languageSnippet The snippet request.
     * @return The rendered HTML.
     */
    String renderLanguageSnippet(LanguageSnippet languageSnippet);

    /**
     * Renders a note macro.
     *
     * @param content The macro content.
     * @return The rendered HTML.
     */
    String renderNote(String content);

    /**
     * Renders a warning macro.
     *
     * @param content The macro content.
     * @return The rendered HTML.
     */
    String renderWarning(String content);

    /**
     * Renders a hidden block macro.
     *
     * @param content The macro content.
     * @return The rendered HTML.
     */
    String renderHidden(String content);

    /**
     * Renders a heading produced by the legacy GDoc header filter.
     *
     * @param level The heading level.
     * @param content The heading content.
     * @return The rendered HTML.
     */
    String renderHeader(String level, String content);

    /**
     * Renders a legacy block quote.
     *
     * @param content The quoted content.
     * @return The rendered HTML.
     */
    String renderBlockQuote(String content);

    /**
     * Renders legacy bold text.
     *
     * @param content The bold content.
     * @return The rendered HTML.
     */
    String renderBold(String content);

    /**
     * Renders legacy inline code.
     *
     * @param content The code content.
     * @return The rendered HTML.
     */
    String renderCode(String content);

    /**
     * Renders legacy italic text.
     *
     * @param content The italic content.
     * @return The rendered HTML.
     */
    String renderItalic(String content);

    /**
     * Renders a legacy image reference.
     *
     * @param src The image source.
     * @return The rendered HTML.
     */
    String renderImage(String src);

    /**
     * Renders a link.
     *
     * @param href The target URL.
     * @param text The link text.
     * @return The rendered HTML.
     */
    String renderLink(String href, String text);

    /**
     * Renders a link with a CSS class.
     *
     * @param href The target URL.
     * @param text The link text.
     * @param cssClass The CSS class.
     * @return The rendered HTML.
     */
    String renderLink(String href, String text, String cssClass);

    /**
     * Renders an external link.
     *
     * @param href The target URL.
     * @param text The link text.
     * @return The rendered HTML.
     */
    String renderExternalLink(String href, String text);

    /**
     * Renders an HTML redirect document.
     *
     * @param target The redirect target.
     * @return The rendered HTML document.
     */
    String renderRedirect(String target);

    /**
     * Renders the full sidebar table of contents.
     *
     * @param tocTree The table of contents tree.
     * @return The rendered HTML.
     */
    String renderTocTree(TocTree tocTree);

    /**
     * Renders the guide summary entries.
     *
     * @param guideSummary The guide summary model.
     * @return The rendered HTML.
     */
    String renderGuideSummary(GuideSummary guideSummary);

    /**
     * Renders the page-local section table of contents.
     *
     * @param sectionToc The section table of contents model.
     * @return The rendered HTML.
     */
    String renderSectionToc(SectionToc sectionToc);

    /**
     * Renders the reference documentation menu.
     *
     * @param referenceMenu The reference menu model.
     * @return The rendered HTML.
     */
    String renderReferenceMenu(ReferenceMenu referenceMenu);

    /**
     * Dependency macro request for rendering both Gradle and Maven snippets.
     *
     * @param groupId The Maven group ID.
     * @param artifactId The artifact ID.
     * @param version The optional version.
     * @param classifier The optional classifier.
     * @param gradleScope The Gradle configuration.
     * @param mavenScope The Maven scope.
     * @param multilanguageCssClass The CSS class used by the language selector.
     * @param title The snippet title.
     */
    record BuildDependency(
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String gradleScope,
        String mavenScope,
        String multilanguageCssClass,
        String title
    ) {
    }

    /**
     * Single build-tool dependency snippet request.
     *
     * @param build The build tool identifier.
     * @param groupId The Maven group ID.
     * @param artifactId The artifact ID.
     * @param version The optional version.
     * @param classifier The optional classifier.
     * @param scope The Gradle configuration or Maven scope.
     * @param multilanguageCssClass The CSS class used by the language selector.
     * @param title The snippet title.
     */
    record Dependency(
        String build,
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String scope,
        String multilanguageCssClass,
        String title
    ) {
    }

    /**
     * Configuration properties converted into the supported config formats.
     *
     * @param title The optional block title.
     * @param samples The converted samples.
     */
    record ConfigurationProperties(String title, List<CodeSample> samples) {
        public ConfigurationProperties {
            samples = List.copyOf(samples);
        }
    }

    /**
     * Language snippet request.
     *
     * @param title The optional snippet title.
     * @param samples The Asciidoc source blocks to render.
     */
    record LanguageSnippet(String title, List<CodeSample> samples) {
        public LanguageSnippet {
            samples = List.copyOf(samples);
        }
    }

    /**
     * Code or Asciidoc source sample for a language selector block.
     *
     * @param language The language identifier.
     * @param source The source text. For language snippets this is an Asciidoc source block.
     */
    record CodeSample(String language, String source) {
    }

    /**
     * Sidebar table of contents tree.
     *
     * @param children The root nodes.
     */
    record TocTree(List<TocNode> children) {
        public TocTree {
            children = List.copyOf(children);
        }
    }

    /**
     * Sidebar table of contents node.
     *
     * @param id The element ID.
     * @param href The link target.
     * @param dataSection The section identifier written to data attributes.
     * @param number The displayed section number.
     * @param title The displayed section title.
     * @param children Child nodes.
     */
    record TocNode(String id, String href, String dataSection, String number, String title, List<TocNode> children) {
        public TocNode {
            children = List.copyOf(children);
        }
    }

    /**
     * Guide summary model.
     *
     * @param items The summary items.
     */
    record GuideSummary(List<GuideSummaryItem> items) {
        public GuideSummary {
            items = List.copyOf(items);
        }
    }

    /**
     * Guide summary item.
     *
     * @param path The path from the current page to the docs root.
     * @param page The encoded guide page name.
     * @param number The chapter number.
     * @param title The chapter title.
     */
    record GuideSummaryItem(String path, String page, String number, String title) {
    }

    /**
     * Page-local section table of contents model.
     *
     * @param items The flattened section items.
     */
    record SectionToc(List<SectionTocItem> items) {
        public SectionToc {
            items = List.copyOf(items);
        }
    }

    /**
     * Page-local section table of contents item.
     *
     * @param marginLeft The legacy left margin in pixels.
     * @param href The target fragment.
     * @param number The displayed section number.
     * @param title The displayed section title.
     */
    record SectionTocItem(int marginLeft, String href, String number, String title) {
    }

    /**
     * Reference documentation menu model.
     *
     * @param categories The menu categories.
     */
    record ReferenceMenu(List<ReferenceMenuCategory> categories) {
        public ReferenceMenu {
            categories = List.copyOf(categories);
        }
    }

    /**
     * Reference documentation menu category.
     *
     * @param title The category title.
     * @param selected Whether this category is selected.
     * @param path The path from the current page to the docs root.
     * @param categoryPath The encoded category path.
     * @param hasUsage Whether a usage page exists.
     * @param items The category items.
     */
    record ReferenceMenuCategory(
        String title,
        boolean selected,
        String path,
        String categoryPath,
        boolean hasUsage,
        List<ReferenceMenuItem> items
    ) {
        public ReferenceMenuCategory {
            items = List.copyOf(items);
        }
    }

    /**
     * Reference documentation menu item.
     *
     * @param sectionPath The encoded section path.
     * @param title The section title.
     */
    record ReferenceMenuItem(String sectionPath, String title) {
    }
}
