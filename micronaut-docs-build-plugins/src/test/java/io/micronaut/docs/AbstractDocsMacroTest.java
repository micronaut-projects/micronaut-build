package io.micronaut.docs;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class AbstractDocsMacroTest {

    private static final Asciidoctor ASCIIDOCTOR = Asciidoctor.Factory.create();

    @TempDir
    private Path testDirectory;

    private DocEngine engine;

    @BeforeEach
    void setUpDocsEngines() {
        var initialContext = new BaseInitialRenderContext();
        initContext(initialContext, "..");
        engine = new DocEngine(initialContext);
        engine.setEngineProperties(engineProperties());
        initialContext.setRenderEngine(engine);

        for (Object macro : customMacros()) {
            setInitialContextIfSupported(macro, initialContext);
            engine.addMacro(macro);
        }

    }

    protected Properties engineProperties() {
        return new Properties();
    }

    protected List<Object> customMacros() {
        return List.of();
    }

    protected final Path testDirectory() {
        return testDirectory;
    }

    protected final String renderGdoc(String input) {
        return renderGdoc(input, Map.of());
    }

    protected final String renderGdoc(String input, Map<String, Object> parameters) {
        var renderContext = new BaseRenderContext();
        initContext(renderContext, "..");
        renderContext.setParameters(parameters);
        renderContext.setRenderEngine(engine);
        return engine.render(input, renderContext);
    }

    protected final String renderAsciidoc(String input) {
        return renderAsciidoc(input, Map.of());
    }

    protected final String renderAsciidoc(String input, Map<String, Object> attributes) {
        var attributesBuilder = Attributes.builder()
            .attribute("source-highlighter", "highlightjs")
            .attribute("sourcedir", testDirectory.toString());
        attributes.forEach(attributesBuilder::attribute);
        var options = Options.builder()
            .safe(SafeMode.SAFE)
            .attributes(attributesBuilder.build())
            .backend("html5")
            .build();
        return ASCIIDOCTOR.convert(input, options);
    }

    protected final Path writeFile(String relativePath, String content) {
        var path = testDirectory.resolve(relativePath);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    protected static void assertHtmlEquals(String expected, String actual) {
        assertEquals(expected.stripTrailing(), actual.stripTrailing());
    }

    private void initContext(BaseRenderContext renderContext, String path) {
        renderContext.set(DocEngine.CONTEXT_PATH, path);
        renderContext.set(DocEngine.BASE_DIR, testDirectory.toString());
        renderContext.set(DocEngine.API_BASE_PATH, testDirectory.resolve("api").toString());
        renderContext.set(DocEngine.API_CONTEXT_PATH, path);
        renderContext.set(DocEngine.RESOURCES_CONTEXT_PATH, path);
        renderContext.set(DocEngine.SOURCE_FILE, "test.gdoc");
    }

    private static void setInitialContextIfSupported(Object macro, BaseRenderContext context) {
        for (Method method : macro.getClass().getMethods()) {
            if (method.getName().equals("setInitialContext") && method.getParameterCount() == 1) {
                try {
                    method.invoke(macro, context);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Cannot set initialContext on " + macro.getClass().getName(), e);
                }
                return;
            }
        }
    }
}
