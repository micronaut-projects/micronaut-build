package io.micronaut.docs

import org.asciidoctor.ast.ContentNode
import org.asciidoctor.ast.StructuralNode
import org.asciidoctor.ast.PhraseNode
import org.asciidoctor.extension.InlineMacroProcessor
/**
 * Inline macro which can be invoked in asciidoc with:
 *
 * dependency:micronaut-spring[version="1.0.1", groupId="io.micronaut"]
 *
 * For
 *
 * Gradle
 * implementation 'io.micronaut:micronaut-spring:1.0.1'
 *
 * Maven
 * <dependency>
 *     <groupId>io.micronaut</groupId>
 *     <artifactId>micronaut-spring</artifactId>
 *     <version>1.0.1</version>
 * </dependency>
 *
 * invoke it with:
 *
 * dependency:micronaut-spring[version="1.0.1", groupId="io.micronaut", verbose="true"]
 *
 * for:
 *
 * Gradle
 * implementation group: 'io.micronaut', name: 'micronaut-spring', version: '1.0.1'
 *
 * Maven
 * <dependency>
 *     <groupId>io.micronaut</groupId>
 *     <artifactId>micronaut-spring</artifactId>
 *     <version>1.0.1</version>
 * </dependency>
 *
 * or simply:
 *
 * Gradle
 * compile 'io.micronaut:micronaut-spring'
 *
 * Maven
 * <dependency>
 * <groupId>io.micronaut</groupId>
 * <artifactId>micronaut-spring</artifactId>
 * </dependency>
 *
 * By default compile scope is used
 *
 * You can use:
 *
 * dependency:micronaut-spring[scope="testCompile"]
 *
 * or specify a different scope for gradle or maven
 *
 * dependency:micronaut-spring[gradleScope="implementation"]
 *
 * A Pyronaut (Python) snippet is rendered as well, for a pyproject.toml file:
 *
 * [tool.pyronaut.dependencies]
 * runtime = [
 *     "io.micronaut:micronaut-spring:1.0.1",
 * ]
 *
 * The Pyronaut scope (runtime, build or test) is derived from the scope attribute:
 * annotation processor and compile only scopes map to build, test scopes map to test
 * and everything else maps to runtime. It can be overridden with:
 *
 * dependency:micronaut-spring[pyronautScope="build"]
 *
 * and the snippet can be omitted for JVM-only dependencies with:
 *
 * dependency:micronaut-kotlin-runtime[pyronaut="false"]
 *
 */
class BuildDependencyMacro extends InlineMacroProcessor implements ValueAtAttributes {
    static final String MICRONAUT_GROUPID = "io.micronaut."
    static final String DEPENDENCY_PREFIX = 'micronaut-'
    static final String GROUPID = 'io.micronaut'
    static final String MULTILANGUAGECSSCLASS = 'multi-language-sample'
    static final String BUILD_GRADLE = 'gradle'
    static final String BUILD_MAVEN = 'maven'
    static final String BUILD_PYRONAUT = 'pyronaut'
    public static final String SCOPE_COMPILE = 'compile'
    public static final String SCOPE_IMPLEMENTATION = 'implementation'
    public static final String PYRONAUT_SCOPE_RUNTIME = 'runtime'
    public static final String PYRONAUT_SCOPE_BUILD = 'build'
    public static final String PYRONAUT_SCOPE_TEST = 'test'
    private static final List<String> PYRONAUT_SCOPES = [PYRONAUT_SCOPE_RUNTIME, PYRONAUT_SCOPE_BUILD, PYRONAUT_SCOPE_TEST]

    BuildDependencyMacro(String macroName) {
        super(macroName)
    }

    BuildDependencyMacro(String macroName, Map<String, Object> config) {
        super(macroName, config)
    }

    @Override
    PhraseNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String content = contentForTargetAndAttributes(target, attributes)
        return createPhraseNode(parent, "quoted", content, [:], [type: ':pass'])
    }

    static String contentForTargetAndAttributes(String target, Map<String, Object> attributes) {
        String groupId
        String artifactId
        String version

        if (target.contains(":")) {
            def tokens = target.split(":")
            groupId = tokens[0] ?: GROUPID
            artifactId = tokens[1]
            if (tokens.length == 3) {
                version = tokens[2]
            } else {
                version = valueAtAttributes('version', attributes)
            }
        } else {
            groupId = valueAtAttributes('groupId', attributes) ?: GROUPID
            artifactId = target.startsWith(DEPENDENCY_PREFIX) ? target : groupId.startsWith(MICRONAUT_GROUPID) ? "${DEPENDENCY_PREFIX}${target}" : target
            version = valueAtAttributes('version', attributes)
        }

        String classifier = valueAtAttributes('classifier', attributes)
        String gradleScope = valueAtAttributes('gradleScope', attributes) ?: toGradleScope(attributes) ?: SCOPE_IMPLEMENTATION
        String mavenScope = valueAtAttributes('mavenScope', attributes) ?: toMavenScope(attributes) ?: SCOPE_COMPILE
        String title = valueAtAttributes('title', attributes) ?: ""
        String content = gradleDependency(BUILD_GRADLE, groupId, artifactId, version, classifier, gradleScope, MULTILANGUAGECSSCLASS, title)
        content += mavenDependency(BUILD_MAVEN, groupId, artifactId, version, classifier, mavenScope, MULTILANGUAGECSSCLASS, title)
        if (isPyronautEnabled(attributes)) {
            String pyronautScope = valueAtAttributes('pyronautScope', attributes) ?: toPyronautScope(attributes)
            content += pyronautDependency(BUILD_PYRONAUT, groupId, artifactId, version, classifier, pyronautScope, MULTILANGUAGECSSCLASS, title)
        }
        content
    }

    static boolean isPyronautEnabled(Map<String, Object> attributes) {
        String pyronaut = valueAtAttributes('pyronaut', attributes)
        pyronaut == null || !'false'.equalsIgnoreCase(pyronaut.trim())
    }

    /**
     * Maps the scope attribute to a Pyronaut dependency scope: {@code build} for annotation processor
     * and compile only scopes, {@code test} for test scopes and {@code runtime} otherwise.
     */
    static String toPyronautScope(Map<String, Object> attributes) {
        String s = valueAtAttributes('scope', attributes) ?: valueAtAttributes('gradleScope', attributes) ?: valueAtAttributes('mavenScope', attributes)
        if (s == null) {
            return PYRONAUT_SCOPE_RUNTIME
        }
        if (s in PYRONAUT_SCOPES) {
            return s
        }
        switch (s) {
            case 'annotationProcessor':
            case 'kapt':
            case 'ksp':
            case 'compileOnly':
            case 'provided':
            case 'developmentOnly':
                return PYRONAUT_SCOPE_BUILD
            default:
                return s.startsWith('test') ? PYRONAUT_SCOPE_TEST : PYRONAUT_SCOPE_RUNTIME
        }
    }

    static String toMavenScope(Map<String, Object> attributes) {
        String s = valueAtAttributes('scope', attributes)
        switch (s) {
            case 'api':
            case 'implementation':
                return 'compile'
            case 'testCompile':
            case 'testRuntime':
            case 'testRuntimeOnly':
            case 'testImplementation':
                return 'test'
            case 'developmentOnly':
            case 'compileOnly': 
                return 'provided'
            case 'runtimeOnly': return 'runtime'
            default: return s
        }
    }

    static String toGradleScope(Map<String, Object> attributes) {
        String s = valueAtAttributes('scope', attributes)
        switch (s) {
            case 'compile':
                return 'implementation'
            case 'testCompile':
                return 'testImplementation'
            case 'test':
                return 'testImplementation'
            case 'runtime':
                return 'runtimeOnly'
            case 'provided':
                return 'developmentOnly'
            default: return s
        }
    }

    static String gradleDependency(String build,
                              String groupId,
                              String artifactId,
                              String version,
                              String classifier,
                              String scope,
                              String multilanguageCssClass,
                              String title) {
        String html = """\
        <div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"title\">$title</div>
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-kotlin hljs" data-lang="${build}">"""

        html += "${scope}(<span class=\"hljs-string\">\"${groupId}:${artifactId}"
        if (version || classifier) {
            html += ":"
        }
        if (version) {
            html += "${version}"
        }
        if (classifier) {
            html += ":${classifier}"
        }
        html += "\")</span>"

        html += """</code></pre>
</div>
</div>
"""
        html
    }

    static String pyronautDependency(String build,
                                     String groupId,
                                     String artifactId,
                                     String version,
                                     String classifier,
                                     String scope,
                                     String multilanguageCssClass,
                                     String title) {
        String coordinates = "${groupId}:${artifactId}"
        if (version || classifier) {
            coordinates += ":"
        }
        if (version) {
            coordinates += version
        }
        if (classifier) {
            coordinates += ":${classifier}"
        }
        String html = """\
<div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"title\">$title</div>
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-toml hljs\" data-lang=\"${build}\">[tool.pyronaut.dependencies]
${scope} = [
    <span class=\"hljs-string\">\"${coordinates}\"</span>,
]</code></pre>
</div>
</div>
"""
        html
    }

    static String mavenDependency(String build,
                              String groupId,
                              String artifactId,
                              String version,
                              String classifier,
                              String scope,
                              String multilanguageCssClass,
                              String title
    ) {
        String html
        if (scope == 'annotationProcessor') {
            html = """\
<div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"title\">$title</div>
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-xml hljs\" data-lang=\"${build}\">&lt;annotationProcessorPaths&gt;
    &lt;path&gt;
        &lt;groupId&gt;${groupId}&lt;/groupId&gt;
        &lt;artifactId&gt;${artifactId}&lt;/artifactId&gt;"""
            if (version) {
                html += "\n        &lt;version&gt;${version}&lt;/version&gt;"
            }
            if (classifier) {
                html += "\n        &lt;classifier&gt;${classifier}&lt;/classifier&gt;"
            }
            html += """
    &lt;/path&gt;
&lt;/annotationProcessorPaths&gt;</code></pre>
</div>
</div>
"""
        } else {

            html = """\
<div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-xml hljs\" data-lang=\"${build}\">&lt;dependency&gt;
    &lt;groupId&gt;${groupId}&lt;/groupId&gt;
    &lt;artifactId&gt;${artifactId}&lt;/artifactId&gt;"""
            if (version) {
                html += "\n    &lt;version&gt;${version}&lt;/version&gt;"
            }
            if (scope != SCOPE_COMPILE) {
                html += "\n    &lt;scope&gt;${scope}&lt;/scope&gt;"
            }
            if (classifier) {
                html += "\n    &lt;classifier&gt;${classifier}&lt;/classifier&gt;"
            }

            html += """
&lt;/dependency&gt;</code></pre>
</div>
</div>
"""
        }
        return html
    }
}
