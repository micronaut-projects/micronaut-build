/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.converter;

import org.apache.commons.lang.StringEscapeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigSlurperGenerator extends AbstractModelVisitor {
    private static final String INDENT = "  ";
    private static final Pattern NON_ASCII_CHARS = Pattern.compile("[^\\p{ASCII}]");
    private static final Set<String> KEYWORDS = Set.of(
            "default",
            "while",
            "for",
            "break",
            "continue",
            "if",
            "else",
            "switch",
            "case",
            "return",
            "try",
            "catch",
            "finally",
            "throw",
            "this",
            "super",
            "new",
            "def",
            "in",
            "as",
            "assert",
            "abstract",
            "final",
            "native",
            "private",
            "protected",
            "public",
            "static",
            "strictfp",
            "synchronized",
            "transient",
            "volatile",
            "class",
            "enum",
            "interface",
            "extends",
            "implements",
            "import",
            "package",
            "instanceof",
            "boolean",
            "byte",
            "char",
            "double",
            "float",
            "int",
            "long",
            "short",
            "void",
            "null",
            "true",
            "false"
    );
    public ConfigSlurperGenerator(Map<String, Object> model) {
        super(model);
    }

    private void indent(Context context) {
        append(INDENT.repeat(Math.max(0, context.depth() - 1)));
    }

    @Override
    public void preVisitMap(Context context, Map<String, Object> map) {
        if (context.depth() > 0) {
            append("{").append(NEWLINE);
        }
    }

    @Override
    public void postVisitMap(Context context, Map<String, Object> map) {
        if (context.depth() > 0) {
            indent(context);
            append("}");
        }
    }

    @Override
    public void visitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        indent(context);
        String escaped = escapeToken(entryKey);
        append(escaped);
        switch (ModelVisitor.kindOf(entryValue)) {
            case MAP -> append(" ");
            default -> append(" = ");
        }
        visit(context, entryValue);
    }

    @Override
    public void postVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        append(NEWLINE);
    }

    @Override
    public void preVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {

    }

    @Override
    public void preVisitList(Context context, List<Object> list) {
        append("[");
    }

    @Override
    public void postVisitList(Context context, List<Object> list) {
        append("]");
    }

    @Override
    public void preVisitListItem(Context context, Object item, boolean isLastItem) {
    }

    @Override
    public void postVisitListItem(Context context, Object item, boolean isLastItem) {
        if (!isLastItem) {
            append(", ");
        }
    }

    @Override
    public void visitListItem(Context context, Object item, boolean isLastItem) {
        visit(context, item);
    }

    @Override
    public void visitObject(Context context, Object object) {
        append(object);
    }

    @Override
    public void visitString(Context context, String value) {
        append("\"").append(StringEscapeUtils.escapeJava(value)).append("\"");
    }

    private static String escapeToken(String text) {
        if (KEYWORDS.contains(text) || NON_ASCII_CHARS.matcher(text).find()) {
            return "'" + text + "'";
        }
        String capitalized = Arrays.stream(text.split("-"))
                .map(s -> s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1))
                .collect(Collectors.joining());
        return text.charAt(0) + capitalized.substring(1);
    }
}
