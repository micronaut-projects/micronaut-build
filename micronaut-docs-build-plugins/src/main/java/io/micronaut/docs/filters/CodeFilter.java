package io.micronaut.docs.filters;

import io.micronaut.docs.internal.RendererResolver;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class CodeFilter extends RegexTokenFilter {
    public CodeFilter() {
        super("@([^\\n]*?)@");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer.append(RendererResolver.renderer(context).renderCode(result.group(1)));
    }
}
