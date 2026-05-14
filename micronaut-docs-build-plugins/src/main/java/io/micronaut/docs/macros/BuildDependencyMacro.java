package io.micronaut.docs.macros;

import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.extension.InlineMacroProcessor;

import java.util.Arrays;
import java.util.Map;

/**
 * Inline macro which can be invoked in asciidoc with:
 *
 * dependency:micronaut-spring[version="1.0.1", groupId="io.micronaut"]
 */
public class BuildDependencyMacro extends InlineMacroProcessor {
    static final String MICRONAUT_GROUPID = "io.micronaut.";
    static final String DEPENDENCY_PREFIX = "micronaut-";
    static final String GROUPID = "io.micronaut";
    static final String MULTILANGUAGECSSCLASS = "multi-language-sample";
    static final String BUILD_GRADLE = "gradle";
    static final String BUILD_MAVEN = "maven";
    public static final String SCOPE_COMPILE = "compile";
    public static final String SCOPE_IMPLEMENTATION = "implementation";

    public BuildDependencyMacro(String macroName) {
        super(macroName);
    }

    public BuildDependencyMacro(String macroName, Map<String, Object> config) {
        super(macroName, config);
    }

    public static String valueAtAttributes(String name, Map<String, Object> attributes) {
        Object textValue = attributes.get("text");
        if (textValue != null) {
            String text = textValue.toString();
            String prefix = name + "=\"";
            if (text.contains(prefix)) {
                String partial = text.substring(text.indexOf(prefix) + prefix.length());
                if (partial.contains("\"")) {
                    return partial.substring(0, partial.indexOf('"'));
                }
                return partial;
            }
        }
        Object value = attributes.get(name);
        return value == null ? null : value.toString();
    }

    @Override
    public PhraseNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String content = contentForTargetAndAttributes(target, attributes);
        return createPhraseNode(parent, "quoted", content, attributes, Map.of("type", ":pass"));
    }

    public static String contentForTargetAndAttributes(String target, Map<String, Object> attributes) {
        String groupId;
        String artifactId;
        String version;

        if (target.contains(":")) {
            String[] tokens = target.split(":");
            groupId = tokens[0].isEmpty() ? GROUPID : tokens[0];
            artifactId = tokens[1];
            if (tokens.length == 3) {
                version = tokens[2];
            } else {
                version = valueAtAttributes("version", attributes);
            }
        } else {
            String configuredGroupId = valueAtAttributes("groupId", attributes);
            groupId = configuredGroupId == null ? GROUPID : configuredGroupId;
            artifactId = target.startsWith(DEPENDENCY_PREFIX) ? target : groupId.startsWith(MICRONAUT_GROUPID) ? DEPENDENCY_PREFIX + target : target;
            version = valueAtAttributes("version", attributes);
        }

        String classifier = valueAtAttributes("classifier", attributes);
        String gradleScope = firstNonNull(valueAtAttributes("gradleScope", attributes), toGradleScope(attributes), SCOPE_IMPLEMENTATION);
        String mavenScope = firstNonNull(valueAtAttributes("mavenScope", attributes), toMavenScope(attributes), SCOPE_COMPILE);
        String title = firstNonNull(valueAtAttributes("title", attributes), "");
        return gradleDependency(BUILD_GRADLE, groupId, artifactId, version, classifier, gradleScope, MULTILANGUAGECSSCLASS, title)
            + mavenDependency(BUILD_MAVEN, groupId, artifactId, version, classifier, mavenScope, MULTILANGUAGECSSCLASS, title);
    }

    public static String toMavenScope(Map<String, Object> attributes) {
        String scope = valueAtAttributes("scope", attributes);
        if (scope == null) {
            return null;
        }
        return switch (scope) {
            case "api", "implementation" -> "compile";
            case "testCompile", "testRuntime", "testRuntimeOnly", "testImplementation" -> "test";
            case "developmentOnly", "compileOnly" -> "provided";
            case "runtimeOnly" -> "runtime";
            default -> scope;
        };
    }

    public static String toGradleScope(Map<String, Object> attributes) {
        String scope = valueAtAttributes("scope", attributes);
        if (scope == null) {
            return null;
        }
        return switch (scope) {
            case "compile" -> "implementation";
            case "testCompile", "test" -> "testImplementation";
            case "runtime" -> "runtimeOnly";
            case "provided" -> "developmentOnly";
            default -> scope;
        };
    }

    public static String gradleDependency(
        String build,
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String scope,
        String multilanguageCssClass,
        String title
    ) {
        StringBuilder html = new StringBuilder("""
            <div class="listingblock %s">
            <div class="title">%s</div>
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-kotlin hljs" data-lang="%s">\
            """.formatted(multilanguageCssClass, title, build));

        html.append(scope).append("(<span class=\"hljs-string\">\"").append(groupId).append(":").append(artifactId);
        if (version != null || classifier != null) {
            html.append(":");
        }
        if (version != null) {
            html.append(version);
        }
        if (classifier != null) {
            html.append(":").append(classifier);
        }
        html.append("\")</span>");

        html.append("""
            </code></pre>
            </div>
            </div>
            """);
        return html.toString();
    }

    public static String mavenDependency(
        String build,
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String scope,
        String multilanguageCssClass,
        String title
    ) {
        StringBuilder html = new StringBuilder();
        if ("annotationProcessor".equals(scope)) {
            html.append("""
                <div class="listingblock %s">
                <div class="title">%s</div>
                <div class="content">
                <pre class="highlightjs highlight"><code class="language-xml hljs" data-lang="%s">&lt;annotationProcessorPaths&gt;
                    &lt;path&gt;
                        &lt;groupId&gt;%s&lt;/groupId&gt;
                        &lt;artifactId&gt;%s&lt;/artifactId&gt;\
                """.formatted(multilanguageCssClass, title, build, groupId, artifactId));
            if (version != null) {
                html.append("\n        &lt;version&gt;").append(version).append("&lt;/version&gt;");
            }
            if (classifier != null) {
                html.append("\n        &lt;classifier&gt;").append(classifier).append("&lt;/classifier&gt;");
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
                """.formatted(multilanguageCssClass, build, groupId, artifactId));
            if (version != null) {
                html.append("\n    &lt;version&gt;").append(version).append("&lt;/version&gt;");
            }
            if (!SCOPE_COMPILE.equals(scope)) {
                html.append("\n    &lt;scope&gt;").append(scope).append("&lt;/scope&gt;");
            }
            if (classifier != null) {
                html.append("\n    &lt;classifier&gt;").append(classifier).append("&lt;/classifier&gt;");
            }
            html.append("""

                &lt;/dependency&gt;</code></pre>
                </div>
                </div>
                """);
        }
        return html.toString();
    }

    private static String firstNonNull(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }
}
