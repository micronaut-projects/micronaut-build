package io.micronaut.docs;

import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class BlockQuoteFilter extends RegexTokenFilter {
    public BlockQuoteFilter() {
        super("(?m)^bc.\\s*?(.*?)\\n\\n");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer.append("<pre class=\"bq\"><code>").append(result.group(1)).append("</code></pre>\n\n");
    }
}
