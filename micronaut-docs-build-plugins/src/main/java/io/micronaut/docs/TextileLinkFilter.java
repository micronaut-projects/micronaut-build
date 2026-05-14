package io.micronaut.docs;

import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;

public class TextileLinkFilter extends RegexTokenFilter {
    public TextileLinkFilter() {
        super("\"([^\"]+?)\":(\\S+?)(\\s)");
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        String text = result.group(1);
        String link = result.group(2);
        String space = result.group(3);

        if (link.startsWith("http://") || link.startsWith("https://")) {
            buffer.append("<a href=\"").append(link).append("\" target=\"blank\">").append(text).append("</a>").append(space);
        } else {
            buffer.append("<a href=\"").append(link).append("\">").append(text).append("</a>").append(space);
        }
    }
}
