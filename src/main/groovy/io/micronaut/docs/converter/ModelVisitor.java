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
import java.util.stream.Stream;

public interface ModelVisitor {
    String NEWLINE = "\n";


    void preVisitMap(Context context, Map<String, Object> map);

    void postVisitMap(Context context, Map<String, Object> map);

    void visitMap(Context context, Map<String, Object> map);

    void visitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast);

    void postVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast);

    void preVisitMapEntry(Context context, String entryKey, Object entryValue, boolean isLast);

    void preVisitList(Context context, List<Object> list);

    void postVisitList(Context context, List<Object> list);

    void preVisitListItem(Context context, Object item, boolean isLastItem);

    void postVisitListItem(Context context, Object item, boolean isLastItem);

    void visitListItem(Context context, Object item, boolean isLastItem);

    void visitList(Context context, List<Object> list);

    void visitString(Context context, String value);

    void visitNumber(Context context, Number value);

    void visitObject(Context context, Object object);

    void visit(Context context, Object object);

    class Context {
        private final Context parent;
        private final String key;
        private final NodeKind parentKind;

        public Context(Context parent, String key, NodeKind parentKind) {
            this.parent = parent;
            this.key = key;
            this.parentKind = parentKind;
        }

        public int depth() {
            if (parent == null) {
                return 0;
            }
            return 1 + parent.depth();
        }

        public String key() {
            return key;
        }

        public NodeKind parentKind() {
            return parentKind;
        }

        public Context child(String childKey, NodeKind parentKind) {
            return new Context(this, childKey, parentKind);
        }

        public Stream<String> pathElements() {
            if (parent == null) {
                return Stream.of();
            }
            return Stream.concat(
                    parent.pathElements(),
                    Stream.of(key())
            );
        }

        public String path() {
            return pathElements().reduce("", (path, item) -> {
                if ("".equals(path)) {
                    return item;
                }
                if (item.startsWith("[")) {
                    return path + item;
                }
                return path + "." + item;
            });
        }
    }

    enum NodeKind {
        MAP,
        LIST,
        STRING,
        NUMBER,
        OBJECT;

        public boolean isComplex() {
            return this == MAP || this == LIST;
        }

        public boolean isMap() {
            return this == MAP;
        }
    }

    static NodeKind kindOf(Object object) {
        Class<?> type = object == null ? Object.class : object.getClass();
        if (Map.class.isAssignableFrom(type)) {
            return NodeKind.MAP;
        } else if (List.class.isAssignableFrom(type)) {
            return NodeKind.LIST;
        } else if (String.class.isAssignableFrom(type)) {
            return NodeKind.STRING;
        } else if (Number.class.isAssignableFrom(type)) {
            return NodeKind.NUMBER;
        } else {
            return NodeKind.OBJECT;
        }
    }
}
