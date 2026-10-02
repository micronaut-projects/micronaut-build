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

import org.tomlj.Toml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates idiomatic TOML: scalar values and arrays of scalars are written as
 * {@code key = value} pairs, nested maps are written as {@code [table]} sections
 * using dotted headers, and lists of maps are written as {@code [[array]]} tables.
 * Intermediate tables which only contain other tables are omitted, so that
 * {@code a: {b: {c: 1}}} renders as {@code [a.b]} followed by {@code c = 1}.
 */
public class TomlGenerator extends AbstractModelVisitor {

    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int MAX_INLINE_ARRAY_LENGTH = 80;
    private static final String INDENT = "  ";

    private final Map<String, Object> model;

    public TomlGenerator(Map<String, Object> model) {
        super(model);
        this.model = model;
    }

    @Override
    public void visit() {
        writeTable(new ArrayList<>(), model, false);
    }

    @Override
    public void visitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        visit(context, entryValue);
    }

    @Override
    public void visitListItem(Context context, Object item, boolean isLastItem) {
        visit(context, item);
    }

    @Override
    public void visitObject(Context context, Object object) {
        append(inlineValue(object));
    }

    @Override
    public void visitString(Context context, String value) {
        append(quote(value));
    }

    @Override
    public void preVisitMap(Context context, Map<String, Object> map) {
    }

    @Override
    public void postVisitMap(Context context, Map<String, Object> map) {
    }

    @Override
    public void preVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
    }

    @Override
    public void postVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
    }

    @Override
    public void preVisitList(Context context, List<Object> list) {
    }

    @Override
    public void postVisitList(Context context, List<Object> list) {
    }

    @Override
    public void preVisitListItem(Context context, Object item, boolean isLastItem) {
    }

    @Override
    public void postVisitListItem(Context context, Object item, boolean isLastItem) {
    }

    /**
     * Writes the content of a table. Key/value pairs are written first, since TOML
     * requires them to appear before any sub-table, followed by sub-tables and
     * arrays of tables.
     *
     * @param path the path of the table
     * @param table the table content
     * @param headerWritten whether the header of the table was already written
     */
    private void writeTable(List<String> path, Map<?, ?> table, boolean headerWritten) {
        List<Map.Entry<?, ?>> values = new ArrayList<>();
        List<Map.Entry<?, ?>> tables = new ArrayList<>();
        List<Map.Entry<?, ?>> arraysOfTables = new ArrayList<>();
        for (Map.Entry<?, ?> entry : table.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                // TOML has no null value
                continue;
            }
            if (value instanceof Map) {
                tables.add(entry);
            } else if (isArrayOfTables(value)) {
                arraysOfTables.add(entry);
            } else {
                values.add(entry);
            }
        }
        boolean onlyContainsTables = values.isEmpty() && !(tables.isEmpty() && arraysOfTables.isEmpty());
        if (!headerWritten && !path.isEmpty() && !onlyContainsTables) {
            writeHeader("[", path, "]");
        }
        for (Map.Entry<?, ?> entry : values) {
            append(key(entry.getKey())).append(" = ");
            writeValue(entry.getValue(), "");
            append(NEWLINE);
        }
        for (Map.Entry<?, ?> entry : tables) {
            writeTable(childPath(path, entry.getKey()), (Map<?, ?>) entry.getValue(), false);
        }
        for (Map.Entry<?, ?> entry : arraysOfTables) {
            List<String> childPath = childPath(path, entry.getKey());
            for (Object item : (List<?>) entry.getValue()) {
                writeHeader("[[", childPath, "]]");
                writeTable(childPath, (Map<?, ?>) item, true);
            }
        }
    }

    private void writeHeader(String open, List<String> path, String close) {
        if (!isEmpty()) {
            append(NEWLINE);
        }
        append(open)
            .append(path.stream().map(TomlGenerator::key).collect(Collectors.joining(".")))
            .append(close)
            .append(NEWLINE);
    }

    private static List<String> childPath(List<String> path, Object key) {
        List<String> childPath = new ArrayList<>(path);
        childPath.add(String.valueOf(key));
        return childPath;
    }

    private void writeValue(Object value, String indent) {
        String inline = inlineValue(value);
        if (!(value instanceof List) || ((List<?>) value).isEmpty() || inline.length() <= MAX_INLINE_ARRAY_LENGTH) {
            append(inline);
            return;
        }
        String itemIndent = indent + INDENT;
        append("[").append(NEWLINE);
        for (Object item : (List<?>) value) {
            if (item != null) {
                append(itemIndent);
                writeValue(item, itemIndent);
                append(",").append(NEWLINE);
            }
        }
        append(indent).append("]");
    }

    private static String inlineValue(Object value) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                return "{}";
            }
            return map.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> key(e.getKey()) + " = " + inlineValue(e.getValue()))
                .collect(Collectors.joining(", ", "{ ", " }"));
        }
        if (value instanceof List) {
            return ((List<?>) value).stream()
                .filter(Objects::nonNull)
                .map(TomlGenerator::inlineValue)
                .collect(Collectors.joining(", ", "[", "]"));
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        return String.valueOf(value);
    }

    private static boolean isArrayOfTables(Object value) {
        if (!(value instanceof List)) {
            return false;
        }
        List<?> list = (List<?>) value;
        return !list.isEmpty() && list.stream().allMatch(item -> item instanceof Map);
    }

    private static String key(Object key) {
        String str = String.valueOf(key);
        if (BARE_KEY.matcher(str).matches()) {
            return str;
        }
        return quote(str);
    }

    private static String quote(String value) {
        return "\"" + Toml.tomlEscape(value) + "\"";
    }

}
