package io.micronaut.docs;

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
        if (img.startsWith("http://") || img.startsWith("https://")) {
            buffer.append("<img border=\"0\" class=\"center\" src=\"").append(img).append("\"></img>");
        } else {
            Object contextPath = context.getRenderContext().get(DocEngine.RESOURCES_CONTEXT_PATH);
            String path = contextPath == null ? "." : contextPath.toString();
            buffer.append("<img border=\"0\" class=\"center\" src=\"").append(path).append("/img/").append(img).append("\"></img>");
        }
    }
}
