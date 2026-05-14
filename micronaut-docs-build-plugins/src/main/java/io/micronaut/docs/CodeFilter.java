package io.micronaut.docs;

import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class CodeFilter extends RegexTokenFilter {
    public CodeFilter() {
        super("@([^\\n]*?)@");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        String text = result.group(1);
        if (text.contains("class=\"code\"")) {
            buffer.append("@").append(text).append("@");
        } else {
            buffer.append("<code>").append(text).append("</code>");
        }
    }
}
