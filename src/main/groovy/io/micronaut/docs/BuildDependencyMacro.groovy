package io.micronaut.docs

import org.asciidoctor.ast.ContentNode
import org.asciidoctor.ast.StructuralNode
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
 */
class BuildDependencyMacro extends InlineMacroProcessor implements ValueAtAttributes {
    static final String MICRONAUT_GROUPID = "io.micronaut."
    static final String DEPENDENCY_PREFIX = 'micronaut-'
    static final String GROUPID = 'io.micronaut'
    static final String MULTILANGUAGECSSCLASS = 'multi-language-sample'
    static final String BUILD_GRADLE = 'gradle'
    static final String BUILD_MAVEN = 'maven'
    public static final String SCOPE_COMPILE = 'compile'
    public static final String SCOPE_IMPLEMENTATION = 'implementation'

    BuildDependencyMacro(String macroName) {
        super(macroName)
    }

    BuildDependencyMacro(String macroName, Map<String, Object> config) {
        super(macroName, config)
    }

    @Override
    Object process(ContentNode parent, String target, Map<String, Object> attributes) {
        String content = contentForTargetAndAttributes(target, attributes)
        return createBlock(parent as StructuralNode, "pass", [content]).convert()
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
        content
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
            case 'compileOnly': return 'provided'
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
            break
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
