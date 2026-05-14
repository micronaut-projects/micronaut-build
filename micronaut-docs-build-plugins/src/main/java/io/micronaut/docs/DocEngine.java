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
package io.micronaut.docs;

import io.micronaut.docs.filters.BlockQuoteFilter;
import io.micronaut.docs.filters.BoldFilter;
import io.micronaut.docs.filters.CodeFilter;
import io.micronaut.docs.filters.HeaderFilter;
import io.micronaut.docs.filters.ImageFilter;
import io.micronaut.docs.filters.ItalicFilter;
import io.micronaut.docs.filters.LinkTestFilter;
import io.micronaut.docs.filters.TextileLinkFilter;
import io.micronaut.docs.internal.StringEscapeCategory;
import io.micronaut.docs.macros.NoteMacro;
import io.micronaut.docs.macros.WarningMacro;
import org.radeox.api.engine.WikiRenderEngine;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.filter.EscapeFilter;
import org.radeox.filter.FilterPipe;
import org.radeox.filter.KeyFilter;
import org.radeox.filter.LineFilter;
import org.radeox.filter.MacroFilter;
import org.radeox.filter.MarkFilter;
import org.radeox.filter.NewlineFilter;
import org.radeox.filter.ParagraphFilter;
import org.radeox.filter.ParamFilter;
import org.radeox.filter.StrikeThroughFilter;
import org.radeox.filter.TypographyFilter;
import org.radeox.filter.context.FilterContext;
import org.radeox.filter.regex.RegexFilter;
import org.radeox.filter.regex.RegexTokenFilter;
import org.radeox.macro.MacroLoader;
import org.radeox.macro.Repository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * A Radeox Wiki engine for generating documentation using a confluence style syntax.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public class DocEngine extends BaseRenderEngine implements WikiRenderEngine {

    public static final String CONTEXT_PATH = "contextPath";
    public static final String SOURCE_FILE = "sourceFile";
    public static final String BASE_DIR = "base.dir";
    public static final String API_BASE_PATH = "apiBasePath";
    public static final String API_CONTEXT_PATH = "apiContextPath";
    public static final String RESOURCES_CONTEXT_PATH = "resourcesContextPath";

    static final Map<String, Object> EXTERNAL_DOCS = new HashMap<>();
    static final Map<String, Object> ALIAS = new HashMap<>();
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

    private final String basedir;
    private DocsMacroFilter macroFilter;
    private MacroLoader macroLoader;
    private Properties engineProperties = new Properties();

    public DocEngine(InitialRenderContext context) {
        super(context);
        Object baseDir = context.get(BASE_DIR);
        basedir = baseDir == null ? "." : baseDir.toString();
    }

    public Properties getEngineProperties() {
        return engineProperties;
    }

    public void setEngineProperties(Properties engineProperties) {
        this.engineProperties = engineProperties;
    }

    @Override
    public boolean exists(String name) {
        int barIndex = name.indexOf('|');
        if (barIndex > -1) {
            String refItem = name.substring(0, barIndex);
            String refCategory = name.substring(barIndex + 1);

            if (refCategory.startsWith("http://") || refCategory.startsWith("https://")) {
                return true;
            }

            if (refCategory.startsWith("guide:")) {
                String alias = refCategory.substring(6);

                if (ALIAS.get(alias) != null) {
                    alias = ALIAS.get(alias).toString();
                }
                String ref = basedir + "/guide/" + alias + ".gdoc";
                File file = new File(ref);
                if (file.exists()) {
                    return true;
                }

                emitWarning(name, ref, "page");
            } else if (refCategory.startsWith("api:")) {
                String ref = refCategory.substring(4);
                if (EXTERNAL_DOCS.keySet().stream().anyMatch(ref::startsWith)) {
                    return true;
                }

                ref = ref.replace('.', '/');
                int anchorIndex = ref.indexOf('#');
                if (anchorIndex > -1) {
                    ref = ref.substring(0, anchorIndex);
                }

                Object apiBase = initialContext.get(API_BASE_PATH);
                if (apiBase != null) {
                    for (String dir : List.of("api", "gapi")) {
                        String path = apiBase + "/" + dir + "/" + ref + ".html";
                        if (new File(path).exists()) {
                            return true;
                        }
                    }
                }

                emitWarning(name, ref, "class");
            } else {
                String dir = getNaturalName(refCategory);
                String ref = basedir + "/ref/" + dir + "/" + refItem + ".gdoc";
                File file = new File(ref);
                if (file.exists()) {
                    return true;
                }

                emitWarning(name, ref, "page");
            }
        }

        return false;
    }

    private void emitWarning(String name, String ref, String type) {
        System.out.println("WARNING: " + initialContext.get(SOURCE_FILE) + ": Link '" + name + "' refers to non-existent " + type + " " + ref + "!");
    }

    @Override
    public boolean showCreate() {
        return false;
    }

    public void addMacro(Object macro) {
        init();
        macroLoader.add(macroFilter.repository(), macro);
    }

    @Override
    protected void init() {
        if (engineProperties != null) {
            engineProperties.forEach((key, value) -> {
                String stringKey = key.toString();
                if (stringKey.startsWith("api.")) {
                    EXTERNAL_DOCS.put(stringKey.substring(4), value);
                } else if (stringKey.startsWith("alias.")) {
                    ALIAS.put(stringKey.substring(6), value);
                }
            });
        }

        if (fp == null) {
            fp = new FilterPipe(initialContext);

            List<RegexFilter> filters = List.of(
                new ParamFilter(),
                new DocsMacroFilter(),
                new TextileLinkFilter(),
                new HeaderFilter(),
                new BlockQuoteFilter(),
                new io.micronaut.docs.filters.ListFilter(),
                new LineFilter(),
                new StrikeThroughFilter(),
                new NewlineFilter(),
                new ParagraphFilter(),
                new BoldFilter(),
                new CodeFilter(),
                new ItalicFilter(),
                new LinkTestFilter(),
                new ImageFilter(),
                new MarkFilter(),
                new KeyFilter(),
                new TypographyFilter(),
                new EscapeFilter()
            );

            for (RegexFilter filter : filters) {
                fp.addFilter(filter);

                if (filter instanceof DocsMacroFilter docsMacroFilter) {
                    macroFilter = docsMacroFilter;
                    macroLoader = new MacroLoader();

                    Repository repository = docsMacroFilter.repository();
                    macroLoader.add(repository, new WarningMacro());
                    macroLoader.add(repository, new NoteMacro());
                }
            }
            fp.init();
        }
    }

    @Override
    public void appendLink(StringBuffer buffer, String name, String view, String anchor) {
        Object contextPath = initialContext.get(CONTEXT_PATH);

        if (name.startsWith("guide:")) {
            String alias = name.substring(6);
            if (ALIAS.get(alias) != null) {
                alias = ALIAS.get(alias).toString();
            }

            int i = alias.lastIndexOf('/');
            if (i >= 0) {
                alias = alias.substring(i + 1);
            }

            buffer.append("<a href=\"")
                .append(contextPath)
                .append("/guide/single.html#")
                .append(StringEscapeCategory.encodeAsUrlFragment(alias))
                .append("\" class=\"guide\">")
                .append(view)
                .append("</a>");
        } else if (name.startsWith("api:")) {
            String link = name.substring(4);

            String externalKey = EXTERNAL_DOCS.keySet().stream()
                .filter(link::startsWith)
                .findFirst()
                .orElse(null);
            link = link.replace('.', '/') + ".html";

            if (externalKey != null) {
                buffer.append("<a href=\"")
                    .append(EXTERNAL_DOCS.get(externalKey))
                    .append("/")
                    .append(link)
                    .append(anchor == null || anchor.isEmpty() ? "" : "#" + anchor)
                    .append("\" class=\"api\">")
                    .append(view)
                    .append("</a>");
            } else {
                Object apiBase = initialContext.get(API_BASE_PATH);
                contextPath = initialContext.get(API_CONTEXT_PATH);

                String apiDir = null;
                for (String dir : List.of("api", "gapi")) {
                    if (new File(apiBase + "/" + dir + "/" + link).exists()) {
                        apiDir = dir;
                        break;
                    }
                }
                buffer.append("<a href=\"")
                    .append(contextPath)
                    .append("/")
                    .append(apiDir)
                    .append("/")
                    .append(link)
                    .append(anchor == null || anchor.isEmpty() ? "" : "#" + anchor)
                    .append("\" class=\"api\">")
                    .append(view)
                    .append("</a>");
            }
        } else {
            String dir = getNaturalName(name);
            String link = contextPath + "/ref/" + dir + "/" + view + ".html";
            buffer.append("<a href=\"").append(link).append("\" class=\"").append(name).append("\">").append(view).append("</a>");
        }
    }

    @Override
    public void appendLink(StringBuffer buffer, String name, String view) {
        appendLink(buffer, name, view, "");
    }

    @Override
    public void appendCreateLink(StringBuffer buffer, String name, String view) {
        buffer.append(name);
    }

    /**
     * Converts a property name into its natural language equivalent eg ('firstName' becomes 'First Name').
     *
     * @param name The property name to convert
     * @return The converted property name
     */
    public String getNaturalName(String name) {
        if (NAME_CACHE.get(name) != null) {
            return NAME_CACHE.get(name);
        }

        List<String> words = new ArrayList<>();
        int i = 0;
        char[] chars = name.toCharArray();
        for (char c : chars) {
            String w;
            if (i >= words.size()) {
                w = "";
                words.add(i, w);
            } else {
                w = words.get(i);
            }

            if (Character.isLowerCase(c) || Character.isDigit(c)) {
                if (Character.isLowerCase(c) && w.isEmpty()) {
                    c = Character.toUpperCase(c);
                } else if (w.length() > 1 && Character.isUpperCase(w.charAt(w.length() - 1))) {
                    w = "";
                    words.add(++i, w);
                }

                words.set(i, w + c);
            } else if (Character.isUpperCase(c)) {
                if ((i == 0 && w.isEmpty()) || Character.isUpperCase(w.charAt(w.length() - 1))) {
                    words.set(i, w + c);
                } else {
                    words.add(++i, String.valueOf(c));
                }
            }
        }

        String naturalName = String.join(" ", words);
        NAME_CACHE.put(name, naturalName);
        return naturalName;
    }

    private static final class DocsMacroFilter extends MacroFilter {
        Repository repository() {
            return getMacroRepository();
        }
    }
}
