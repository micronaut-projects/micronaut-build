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
import org.tomlj.TomlParseResult;

import java.util.List;
import java.util.Map;

public class TomlGenerator extends AbstractModelVisitor {

    public TomlGenerator(Map<String, Object> model) {
        super(model);
    }


    @Override
    public void preVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        String escaped = Toml.tomlEscape(entryKey).toString();
        boolean addQuotes = !escaped.equals(entryKey) || entryKey.contains(".");
        if (addQuotes) {
            append("\"");
        }
        append(escaped);
        if (addQuotes) {
            append("\"");
        }
        append(" = ");
    }

    @Override
    public void postVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        if (context.depth() == 1) {
            append(NEWLINE);
        } else if (context.depth() > 1 && !isLast) {
            append(", ");
        }
    }

    @Override
    public void preVisitMap(Context context, Map<String, Object> map) {
        if (context.depth()>0) {
            append("{");
        }
    }

    @Override
    public void postVisitMap(Context context, Map<String, Object> map) {
        if (context.depth() > 0) {
            append("}");
        }
    }

    @Override
    public void visitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        visit(context, entryValue);
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
        append("\"").append(Toml.tomlEscape(value)).append("\"");
    }

    @Override
    public String toString() {
        // We let the toml library handle the output, which is nicer
        // than what we produce ourselves
        TomlParseResult toml = Toml.parse(super.toString());
        toml.errors().stream().forEach(System.err::println);
        return toml.toToml();
    }

}
