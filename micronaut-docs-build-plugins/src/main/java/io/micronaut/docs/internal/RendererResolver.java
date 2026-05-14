package io.micronaut.docs.internal;

import io.micronaut.docs.DefaultRenderer;
import io.micronaut.docs.DocEngine;
import io.micronaut.docs.Renderer;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.filter.context.FilterContext;

/**
 * Resolves the active renderer from Radeox render contexts.
 */
public final class RendererResolver {

    private RendererResolver() {
    }

    /**
     * Resolves the renderer for a filter context.
     *
     * @param context The filter context.
     * @return The active renderer.
     */
    public static Renderer renderer(FilterContext context) {
        return context == null ? DefaultRenderer.INSTANCE : renderer(context.getRenderContext());
    }

    /**
     * Resolves the renderer for a render context.
     *
     * @param context The render context.
     * @return The active renderer.
     */
    public static Renderer renderer(RenderContext context) {
        if (context != null && context.getRenderEngine() instanceof DocEngine engine) {
            return engine.getRenderer();
        }
        return DefaultRenderer.INSTANCE;
    }
}
