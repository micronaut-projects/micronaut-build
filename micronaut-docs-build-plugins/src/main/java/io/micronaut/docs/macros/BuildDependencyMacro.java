package io.micronaut.docs.macros;

import io.micronaut.docs.DefaultRenderer;
import io.micronaut.docs.Renderer;
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
    private final Renderer renderer;

    public BuildDependencyMacro(String macroName) {
        this(macroName, DefaultRenderer.INSTANCE);
    }

    /**
     * Creates a dependency macro with a custom renderer.
     *
     * @param macroName The macro name.
     * @param renderer The renderer to use.
     */
    public BuildDependencyMacro(String macroName, Renderer renderer) {
        super(macroName);
        this.renderer = renderer == null ? DefaultRenderer.INSTANCE : renderer;
    }

    public BuildDependencyMacro(String macroName, Map<String, Object> config) {
        super(macroName, config);
        this.renderer = DefaultRenderer.INSTANCE;
    }

    /**
     * Creates a configured dependency macro with a custom renderer.
     *
     * @param macroName The macro name.
     * @param config The macro configuration.
     * @param renderer The renderer to use.
     */
    public BuildDependencyMacro(String macroName, Map<String, Object> config, Renderer renderer) {
        super(macroName, config);
        this.renderer = renderer == null ? DefaultRenderer.INSTANCE : renderer;
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
        String content = contentForTargetAndAttributes(target, attributes, renderer);
        return createPhraseNode(parent, "quoted", content, attributes, Map.of("type", ":pass"));
    }

    public static String contentForTargetAndAttributes(String target, Map<String, Object> attributes) {
        return contentForTargetAndAttributes(target, attributes, DefaultRenderer.INSTANCE);
    }

    /**
     * Creates rendered dependency content for a macro target and attributes.
     *
     * @param target The dependency macro target.
     * @param attributes The macro attributes.
     * @param renderer The renderer to use.
     * @return The rendered dependency HTML.
     */
    public static String contentForTargetAndAttributes(String target, Map<String, Object> attributes, Renderer renderer) {
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
        return renderer.renderBuildDependency(new Renderer.BuildDependency(
            groupId,
            artifactId,
            version,
            classifier,
            gradleScope,
            mavenScope,
            MULTILANGUAGECSSCLASS,
            title
        ));
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
        return DefaultRenderer.INSTANCE.renderGradleDependency(new Renderer.Dependency(
            build,
            groupId,
            artifactId,
            version,
            classifier,
            scope,
            multilanguageCssClass,
            title
        ));
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
        return DefaultRenderer.INSTANCE.renderMavenDependency(new Renderer.Dependency(
            build,
            groupId,
            artifactId,
            version,
            classifier,
            scope,
            multilanguageCssClass,
            title
        ));
    }

    private static String firstNonNull(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }
}
