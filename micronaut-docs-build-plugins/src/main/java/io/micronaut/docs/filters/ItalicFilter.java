package io.micronaut.docs.filters;

import io.micronaut.docs.internal.RendererResolver;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class ItalicFilter extends RegexTokenFilter {
    public ItalicFilter() {
        super("\\b_([^\\n]*?)_\\b");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer.append(RendererResolver.renderer(context).renderItalic(result.group(1)));
    }
}
