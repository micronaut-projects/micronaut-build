package io.micronaut.docs.filters;

import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class ItalicFilter extends RegexTokenFilter {
    public ItalicFilter() {
        super("\\b_([^\\n]*?)_\\b");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer.append(" <em class=\"italic\">").append(result.group(1)).append("</em> ");
    }
}
