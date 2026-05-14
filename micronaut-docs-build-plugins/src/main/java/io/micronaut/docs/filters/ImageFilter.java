package io.micronaut.docs.filters;

import io.micronaut.docs.DocEngine;
import io.micronaut.docs.internal.RendererResolver;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class ImageFilter extends RegexTokenFilter {
    public ImageFilter() {
        super("!([^\\n<>=]*?\\.(jpg|png|gif))!");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        String img = result.group(1);
        String src;
        if (img.startsWith("http://") || img.startsWith("https://")) {
            src = img;
        } else {
            Object contextPath = context.getRenderContext().get(DocEngine.RESOURCES_CONTEXT_PATH);
            String path = contextPath == null ? "." : contextPath.toString();
            src = path + "/img/" + img;
        }
        buffer.append(RendererResolver.renderer(context).renderImage(src));
    }
}
