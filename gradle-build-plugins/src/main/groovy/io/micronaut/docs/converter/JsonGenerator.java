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

import java.util.List;
import java.util.Map;

public class JsonGenerator extends AbstractModelVisitor {
    private static final String INDENT = "  ";

    public JsonGenerator(Map<String, Object> model) {
        super(model);
    }

    private void indent(Context context) {
        for (int i = 0; i < context.depth(); i++) {
            append(INDENT);
        }
    }

    @Override
    public void preVisitMap(Context context, Map<String, Object> map) {
        append("{").append(NEWLINE);
    }

    @Override
    public void postVisitMap(Context context, Map<String, Object> map) {
        indent(context);
        append("}");
    }

    @Override
    public void visitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        indent(context);
        String escaped = escape(entryKey).toString();
        append("\"");
        append(escaped);
        append("\": ");
        visit(context, entryValue);
    }

    @Override
    public void postVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        if (!isLast) {
            append(", ");
        }
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
        append("\"").append(escape(value)).append("\"");
    }

    private static StringBuilder escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                out.append("\\\"");
                continue;
            }
            if (ch == '\\') {
                out.append("\\\\");
                continue;
            }
            if (ch >= 0x20) {
                out.append(ch);
                continue;
            }

            switch (ch) {
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    out.append("\\u").append(String.format("%04x", text.codePointAt(i)));
            }
        }
        return out;
    }
}
