/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.tomlj.TomlType.TABLE;
import static org.tomlj.TomlType.typeFor;

/**
 * This class is mostly copied from {@link JsonSerializer}.
 * It is responsible for converting a TOML structure into Java properties equivalent.
 */
public class JavaPropertiesSerializer {
    private static final String LINE_BREAK = "\n";

    private JavaPropertiesSerializer() {

    }

    public static void toJavaProperties(TomlTable table, Appendable appendable) throws IOException {
        requireNonNull(table);
        requireNonNull(appendable);
        toJavaProperties(table, appendable, "");
        appendable.append(LINE_BREAK);
    }

    private static void toJavaProperties(TomlTable table, Appendable appendable, String path)
            throws IOException {
        if (table.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<String, Object>> iterator = table.entrySet().stream().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Object> entry = iterator.next();
            String key = entry.getKey();
            Object value = entry.getValue();
            assert value != null;
            appendTomlValue(value, appendable, appendPath(path, key));
        }
    }

    private static String appendPath(String path, String element) {
        if ("".equals(path)) {
            return element;
        }
        if (element.startsWith("[")) {
            return path + element;
        }
        return path + "." + element;
    }

    private static void toJavaProperties(TomlArray array, Appendable appendable, String path)
            throws IOException {
        if (array.isEmpty()) {
            return;
        }

        int i = 0;
        for (Object tomlValue : array.toList()) {
            Optional<TomlType> tomlType = typeFor(tomlValue);
            assert tomlType.isPresent();
            if (tomlType.get().equals(TABLE)) {
                toJavaProperties((TomlTable) tomlValue, appendable, appendPath(path, "[" + i + "]"));
            } else {
                appendTomlValue(tomlValue, appendable, appendPath(path, "[" + i + "]"));
            }
            i++;
        }
    }

    private static void appendTomlValue(Object value, Appendable appendable, String path)
            throws IOException {
        Optional<TomlType> tomlType = typeFor(value);
        assert tomlType.isPresent();
        switch (tomlType.get()) {
            case ARRAY:
                toJavaProperties((TomlArray) value, appendable, path);
                return;
            case TABLE:
                toJavaProperties((TomlTable) value, appendable, path);
                return;
            default:
                // continue
        }

        appendTomlValueLiteral(tomlType.get(), value, appendable, path);
    }

    private static void appendTomlValueLiteral(
            TomlType tomlType,
            Object value,
            Appendable appendable,
            String path) throws IOException {
        appendable.append(path).append("=");
        switch (tomlType) {
            case STRING:
                appendable.append(escape((String) value));
                break;
            case INTEGER:
                appendable.append(value.toString());
                break;
            case FLOAT:
                if (Double.isNaN((Double) value)) {
                    appendable.append("nan");
                } else if ((Double) value == Double.POSITIVE_INFINITY) {
                    appendable.append("+inf");
                } else if ((Double) value == Double.NEGATIVE_INFINITY) {
                    appendable.append("-inf");
                } else {
                    appendable.append(value.toString());
                }
                break;
            case BOOLEAN:
                appendable.append(((Boolean) value) ? "true" : "false");
                break;
            case OFFSET_DATE_TIME:
                appendable.append(((OffsetDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                break;
            case LOCAL_DATE_TIME:
                appendable.append(((LocalDateTime) value).format(DateTimeFormatter.ISO_DATE_TIME));
                break;
            case LOCAL_DATE:
                appendable.append(((LocalDate) value).format(DateTimeFormatter.ISO_DATE));
                break;
            case LOCAL_TIME:
                appendable.append(((LocalTime) value).format(DateTimeFormatter.ISO_TIME));
                break;
            default:
                throw new AssertionError("Attempted to output literal form of non-literal type " + tomlType.typeName());
        }
        appendable.append(LINE_BREAK);
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
