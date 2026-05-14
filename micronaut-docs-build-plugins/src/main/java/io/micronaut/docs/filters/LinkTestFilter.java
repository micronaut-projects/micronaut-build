/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.filters;

import org.radeox.api.engine.WikiRenderEngine;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.regex.MatchResult;
import org.radeox.util.Encoder;

/**
 * @author Graeme Rocher
 * @since 1.1
 */
public class LinkTestFilter extends RegexTokenFilter {

    public LinkTestFilter() {
        super("\\[(.*?)\\]");
    }

    protected String getWikiView(String name) {
        return name;
    }

    @Override
    public void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        Object renderEngine = context.getRenderContext().getRenderEngine();

        if (!(renderEngine instanceof WikiRenderEngine engine)) {
            return;
        }

        String name = result.group(1);
        String original = name;

        if (name == null) {
            buffer.append(Encoder.escape(result.group(0)));
            return;
        }

        name = Encoder.unescape(name.trim());

        int pipeIndex = name.indexOf('|');
        String alias = "";
        if (pipeIndex != -1) {
            alias = name.substring(0, pipeIndex);
            name = name.substring(pipeIndex + 1);
        }

        int hashIndex = name.lastIndexOf('#');

        String hash = "";
        if (hashIndex != -1 && hashIndex != name.length() - 1) {
            hash = name.substring(hashIndex + 1);
            name = name.substring(0, hashIndex);
        }

        if (name.contains("http://") || name.contains("https://")) {
            buffer.append("<a href=\"")
                .append(name)
                .append(hash.isEmpty() ? "" : "#" + hash)
                .append("\" target=\"blank\">")
                .append(Encoder.escape(alias))
                .append("</a>");
            return;
        }

        if (engine.exists(original)) {
            String view = getWikiView(name);
            if (pipeIndex != -1) {
                view = alias;
            }
            if (hashIndex != -1) {
                engine.appendLink(buffer, name, view, hash);
            } else {
                engine.appendLink(buffer, name, view);
            }
        } else if (engine.showCreate()) {
            engine.appendCreateLink(buffer, name, getWikiView(name));
            context.getRenderContext().setCacheable(false);
        } else {
            buffer.append(name);
        }
    }
}
