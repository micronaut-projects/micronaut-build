package io.micronaut.docs.filters;

import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class BoldFilter extends RegexTokenFilter {
    public BoldFilter() {
        super("\\*([^\\n]*?)\\*");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer.append("<strong class=\"bold\">").append(result.group(1)).append("</strong>");
    }
}
