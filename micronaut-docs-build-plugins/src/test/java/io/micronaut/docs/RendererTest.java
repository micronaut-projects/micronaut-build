package io.micronaut.docs;

import io.micronaut.docs.asciidoc.AsciiDocEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RendererTest {

    @TempDir
    Path testDirectory;

    @Test
    void docEngineUsesCustomRendererForRadeoxMacrosAndFilters() {
        var initialContext = new BaseInitialRenderContext();
        var engine = new DocEngine(initialContext);
        engine.setRenderer(new DefaultRenderer() {
            @Override
            public String renderNote(String content) {
                return "<aside class=\"custom-note\">" + content + "</aside>";
            }

            @Override
            public String renderHeader(String level, String content) {
                return "<section data-level=\"" + level + "\">" + content + "</section>";
            }
        });
        initialContext.setRenderEngine(engine);

        var renderContext = new BaseRenderContext();
        renderContext.setRenderEngine(engine);

        assertEquals("<aside class=\"custom-note\">Pay attention.</aside>", engine.render("{note}Pay attention.{note}", renderContext));
        assertEquals("<section data-level=\"2\">Section Title</section>", engine.render("h2. Section Title", renderContext));
    }

    @Test
    void asciiDocEngineUsesCustomRendererForBuildDependencyMacro() {
        var initialContext = new BaseInitialRenderContext();
        var engine = new AsciiDocEngine(initialContext);
        engine.setRenderer(new DefaultRenderer() {
            @Override
            public String renderBuildDependency(BuildDependency dependency) {
                return "<dependency data-gradle-scope=\"" + dependency.gradleScope() + "\" data-maven-scope=\"" + dependency.mavenScope() + "\">"
                    + dependency.groupId() + ":" + dependency.artifactId()
                    + "</dependency>";
            }
        });
        initialContext.setRenderEngine(engine);

        var renderContext = new BaseRenderContext();
        renderContext.setRenderEngine(engine);

        assertEquals("""
            <div class="paragraph">
            <p><dependency data-gradle-scope="runtimeOnly" data-maven-scope="runtime">io.micronaut:micronaut-http-client</dependency></p>
            </div>
            """.stripTrailing(), engine.render("dependency:micronaut-http-client[scope=runtime]", renderContext).stripTrailing());
    }

    @Test
    void asciiDocEngineUsesCustomRendererForConfigurationPropertiesMacro() {
        var initialContext = new BaseInitialRenderContext();
        var engine = new AsciiDocEngine(initialContext);
        engine.setRenderer(new DefaultRenderer() {
            @Override
            public String renderConfigurationProperties(ConfigurationProperties configurationProperties) {
                return "<config title=\"" + configurationProperties.title() + "\" samples=\"" + configurationProperties.samples().size() + "\">"
                    + configurationProperties.samples().get(0).source()
                    + "</config>";
            }
        });
        initialContext.setRenderEngine(engine);

        var renderContext = new BaseRenderContext();
        renderContext.setRenderEngine(engine);

        assertEquals("""
            <div class="openblock">
            <div class="content">
            <config title="Server configuration" samples="6">micronaut.server.port=8080</config>
            </div>
            </div>
            """.stripTrailing(), engine.render("""
            [configuration,title="Server configuration"]
            ----
            micronaut:
              server:
                port: 8080
            ----
            """, renderContext).stripTrailing());
    }

    @Test
    void asciiDocEngineUsesCustomRendererForLanguageSnippetMacro() throws IOException {
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

        var initialContext = new BaseInitialRenderContext();
        var engine = new AsciiDocEngine(initialContext);
        engine.getAttributes().put("sourcedir", testDirectory.toString());
        engine.setRenderer(new DefaultRenderer() {
            @Override
            public String renderLanguageSnippet(LanguageSnippet languageSnippet) {
                return "<snippets title=\"" + languageSnippet.title() + "\" samples=\"" + languageSnippet.samples().size() + "\">"
                    + languageSnippet.samples().get(0).source()
                    + "</snippets>";
            }
        });
        initialContext.setRenderEngine(engine);

        var renderContext = new BaseRenderContext();
        renderContext.setRenderEngine(engine);

        assertEquals("""
            <snippets title="Example" samples="4">.Example

            [source.multi-language-sample,java,Example]
            ----
            include::%s[tag=body,indent=0]
            ----

            </snippets>
            """.formatted(testDirectory.resolve("test-suite/src/test/java/example/Foo.java")).stripTrailing(), engine.render(
            "snippet::example.Foo[tags=body,indent=0,title=\"Example\"]",
            renderContext
        ).stripTrailing());
    }

    private void writeFile(String relativePath, String content) throws IOException {
        var path = testDirectory.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
