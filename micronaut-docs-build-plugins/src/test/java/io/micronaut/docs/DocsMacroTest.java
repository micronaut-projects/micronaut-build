package io.micronaut.docs;

import io.micronaut.docs.macros.HiddenMacro;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DocsMacroTest extends AbstractDocsMacroTest {

    @Override
    protected List<Object> customMacros() {
        return List.of(new HiddenMacro());
    }

    @Test
    void rendersNoteMacro() {
        assertHtmlEquals(
            "<blockquote class=\"note\">Pay attention.</blockquote>",
            renderGdoc("{note}Pay attention.{note}")
        );
    }

    @Test
    void rendersWarningMacro() {
        assertHtmlEquals(
            "<blockquote class=\"warning\">Be careful.</blockquote>",
            renderGdoc("{warning}Be careful.{warning}")
        );
    }

    @Test
    void rendersHiddenMacro() {
        assertHtmlEquals(
            "<div class=\"hidden-block\">Internal details.</div>",
            renderGdoc("{hidden}Internal details.{hidden}")
        );
    }

    @Test
    void rendersApiMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/io/micronaut/net/example/ServiceSettings.html">ServiceSettings</a></p>
            </div>
            """, renderAsciidoc("api:net.example.ServiceSettings[]"));
    }

    @Test
    void rendersApiMacroWithTextOption() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/io/micronaut/net/example/ServiceSettings.html">Service settings</a></p>
            </div>
            """, renderAsciidoc("api:net.example.ServiceSettings[text='Service settings']"));
    }

    @Test
    void rendersApiMacroWithPackagePrefixOption() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/titi/tata/net/example/ServiceSettings.html">ServiceSettings</a></p>
            </div>
            """, renderAsciidoc("api:net.example.ServiceSettings[packagePrefix='titi.tata.']"));
    }

    @Test
    void rendersApiMacroWithDefaultUriOption() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/dadidou/io/micronaut/net/example/ServiceSettings.html">ServiceSettings</a></p>
            </div>
            """, renderAsciidoc("api:net.example.ServiceSettings[defaultUri='/dadidou']"));
    }

    @Test
    void rendersApiMacroWithDefaultUriAndPackagePrefixOptions() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/dadidou/net/example/ServiceSettings.html">ServiceSettings</a></p>
            </div>
            """, renderAsciidoc("api:net.example.ServiceSettings[defaultUri='/dadidou', packagePrefix='']"));
    }

    @Test
    void rendersApiMacroWithMethodReference() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/io/micronaut/context/ApplicationContext.html#run-java.lang.Class-">ApplicationContext.run(java.lang.Class)</a></p>
            </div>
            """, renderAsciidoc("api:context.ApplicationContext.run(java.lang.Class)[]"));
    }

    @Test
    void rendersApiMacroWithPropertyReference() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/io/micronaut/context/ApplicationContext.html#environment">environment</a></p>
            </div>
            """, renderAsciidoc("api:context.ApplicationContext#environment[]"));
    }

    @Test
    void rendersAnnotationMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/io/micronaut/context/annotation/Requires.html">@Requires</a></p>
            </div>
            """, renderAsciidoc("ann:context.annotation.Requires[]"));
    }

    @Test
    void rendersPackageMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/io/micronaut/http/package-summary.html">io.micronaut.http</a></p>
            </div>
            """, renderAsciidoc("pkg:http[]"));
    }

    @Test
    void rendersPackageMacroWithTextOption() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="../api/io/micronaut/http/package-summary.html">HTTP APIs</a></p>
            </div>
            """, renderAsciidoc("pkg:http[text='HTTP APIs']"));
    }

    @Test
    void rendersMicronautApiMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="https://docs.micronaut.io/latest/api/io/micronaut/context/ApplicationContext.html">ApplicationContext</a></p>
            </div>
            """, renderAsciidoc("mnapi:io.micronaut.context.ApplicationContext[]"));
    }

    @Test
    void rendersMicronautApiMacroWithDocumentAttributeBaseUri() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/mn-api/io/micronaut/context/ApplicationContext.html">ApplicationContext</a></p>
            </div>
            """, renderAsciidoc("mnapi:io.micronaut.context.ApplicationContext[]", Map.of("micronautApi", "/mn-api")));
    }

    @Test
    void rendersMicronautApiMacroWithLowercaseDocumentAttributeBaseUri() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/mn-api/io/micronaut/context/ApplicationContext.html">ApplicationContext</a></p>
            </div>
            """, renderAsciidoc("mnapi:io.micronaut.context.ApplicationContext[]", Map.of("micronautapi", "/mn-api")));
    }

    @Test
    void rendersJdkApiMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html">CompletableFuture</a></p>
            </div>
            """, renderAsciidoc("jdk:java.util.concurrent.CompletableFuture[]"));
    }

    @Test
    void rendersJdkApiMacroWithModuleOption() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.logging/java/util/logging/ConsoleHandler.html">ConsoleHandler</a></p>
            </div>
            """, renderAsciidoc("jdk:java.util.logging.ConsoleHandler[module=java.logging]"));
    }

    @Test
    void rendersJdkApiMacroWithDocumentAttributeBaseUri() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/jdk-api/java.base/java/util/List.html">List</a></p>
            </div>
            """, renderAsciidoc("jdk:java.util.List[]", Map.of("jdkapi", "/jdk-api")));
    }

    @Test
    void rendersJeeApiMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="https://docs.oracle.com/javaee/6/api/java.base/javax/annotation/PostConstruct.html">PostConstruct</a></p>
            </div>
            """, renderAsciidoc("jee:javax.annotation.PostConstruct[]"));
    }

    @Test
    void rendersJeeApiMacroWithDocumentAttributeBaseUri() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/jee-api/java.base/javax/annotation/PostConstruct.html">PostConstruct</a></p>
            </div>
            """, renderAsciidoc("jee:javax.annotation.PostConstruct[]", Map.of("jeeapi", "/jee-api")));
    }

    @Test
    void rendersReactiveStreamsApiMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="https://www.reactive-streams.org/reactive-streams-1.0.3-javadoc/org/reactivestreams/Publisher.html">Publisher</a></p>
            </div>
            """, renderAsciidoc("rs:Publisher[]"));
    }

    @Test
    void rendersReactiveStreamsApiMacroWithDocumentAttributeBaseUri() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/rs-api/org/reactivestreams/Publisher.html">Publisher</a></p>
            </div>
            """, renderAsciidoc("rs:Publisher[]", Map.of("rsapi", "/rs-api")));
    }

    @Test
    void rendersRxJavaApiMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Flowable.html">Flowable</a></p>
            </div>
            """, renderAsciidoc("rx:Flowable[]"));
    }

    @Test
    void rendersRxJavaApiMacroWithDocumentAttributeBaseUri() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/rx-api/io/reactivex/Flowable.html">Flowable</a></p>
            </div>
            """, renderAsciidoc("rx:Flowable[]", Map.of("rxapi", "/rx-api")));
    }

    @Test
    void rendersReactorJavaApiMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html">Flux</a></p>
            </div>
            """, renderAsciidoc("reactor:Flux[]"));
    }

    @Test
    void rendersReactorJavaApiMacroWithDocumentAttributeBaseUri() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><a href="/reactor-api/reactor/core/publisher/Flux.html">Flux</a></p>
            </div>
            """, renderAsciidoc("reactor:Flux[]", Map.of("reactorapi", "/reactor-api")));
    }

    @Test
    void rendersBuildDependencyMacro() {
        assertHtmlEquals("""
            <div class="paragraph">
            <p><div class="listingblock multi-language-sample">
            <div class="title">null</div>
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-kotlin hljs" data-lang="gradle">runtimeOnly(<span class="hljs-string">"io.micronaut:micronaut-http-client")</span></code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-xml hljs" data-lang="maven">&lt;dependency&gt;
                &lt;groupId&gt;io.micronaut&lt;/groupId&gt;
                &lt;artifactId&gt;micronaut-http-client&lt;/artifactId&gt;
                &lt;scope&gt;runtime&lt;/scope&gt;
            &lt;/dependency&gt;</code></pre>
            </div>
            </div>
            </p>
            </div>
            """, renderAsciidoc("dependency:micronaut-http-client[scope=runtime]"));
    }

    @Test
    void rendersConfigurationPropertiesMacro() {
        assertHtmlEquals("""
            <div class="openblock">
            <div class="content">
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-properties hljs" data-lang="properties">micronaut.server.port=8080</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-yaml hljs" data-lang="yaml">micronaut:
              server:
                port: 8080</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-toml hljs" data-lang="toml">[micronaut]
              [micronaut.server]
                port=8080</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-groovy-config hljs" data-lang="groovy-config">micronaut {
              server {
                port = 8080
              }
            }</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-hocon hljs" data-lang="hocon">{
              micronaut {
                server {
                  port = 8080
                }
              }
            }</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-json-config hljs" data-lang="json-config">{
              "micronaut": {
                "server": {
                  "port": 8080
                }
              }
            }</code></pre>
            </div>
            </div>
            </div>
            </div>
            """, renderAsciidoc("""
            [configuration]
            ----
            micronaut:
              server:
                port: 8080
            ----
            """));
    }

    @Test
    void rendersLanguageSnippetMacro() {
        writeFile("test-suite/src/test/java/example/Foo.java", """
            // tag::body[]
            class Foo {}
            // end::body[]
            """);
        writeFile("test-suite-python/src/test/python/example/Foo.py", """
            # tag::body[]
            class Foo:
                pass
            # end::body[]
            """);
        writeFile("test-suite-kotlin/src/test/kotlin/example/Foo.kt", """
            // tag::body[]
            class Foo
            // end::body[]
            """);
        writeFile("test-suite-groovy/src/test/groovy/example/Foo.groovy", """
            // tag::body[]
            class Foo {}
            // end::body[]
            """);

        assertHtmlEquals("""
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-java hljs" data-lang="java">class Foo {}</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-python hljs" data-lang="python">class Foo:
                pass</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-kotlin hljs" data-lang="kotlin">class Foo</code></pre>
            </div>
            </div>
            <div class="listingblock multi-language-sample">
            <div class="content">
            <pre class="highlightjs highlight"><code class="language-groovy hljs" data-lang="groovy">class Foo {}</code></pre>
            </div>
            </div>
            """, renderAsciidoc("snippet::example.Foo[tags=body,indent=0]"));
    }
}
