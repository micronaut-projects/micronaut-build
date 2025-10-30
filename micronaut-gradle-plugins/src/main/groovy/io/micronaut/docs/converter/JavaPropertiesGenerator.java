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
import java.util.regex.Pattern;

public class JavaPropertiesGenerator extends AbstractModelVisitor {
    private static final Pattern NON_ASCII_CHARS = Pattern.compile("[^\\p{ASCII}]");

    public JavaPropertiesGenerator(Map<String, Object> model) {
        super(model);
    }

    @Override
    public void visitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {
        visit(context, entryValue);
    }

    @Override
    public void preVisitMap(Context context, Map<String, Object> map) {

    }

    @Override
    public void postVisitMap(Context context, Map<String, Object> map) {

    }

    @Override
    public void postVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {

    }

    @Override
    public void preVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast) {

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

    @Override
    public void visitListItem(Context context, Object item, boolean isLastItem) {
        visit(context, item);
    }

    @Override
    public void visitObject(Context context, Object scalar) {
        String path = context.pathElements().reduce("", (cur, item) -> {
            String escaped = escape(item);
            if ("".equals(cur)) {
                return escaped;
            }
            if (item.startsWith("[")) {
                return cur + escaped;
            }
            return cur + "." + escaped;
        });
        append(path).append("=").append(scalar);
        append(NEWLINE);
    }

    private static String escape(String str) {
        return NON_ASCII_CHARS.matcher(str).replaceAll("\\\\$0");
    }

}
