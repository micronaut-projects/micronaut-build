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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class AbstractModelVisitor implements ModelVisitor {
    private final Map<String, Object> model;
    private final StringBuilder sb = new StringBuilder();

    public AbstractModelVisitor(Map<String, Object> model) {
        this.model = model;
    }

    @Override
    public void visitMap(Context context, Map<String, Object> map) {
        preVisitMap(context, map);
        for (Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Object> entry = iterator.next();
            String childKey = entry.getKey();
            Object value = entry.getValue();
            Context child = context.child(childKey, NodeKind.MAP);
            boolean isLast = !iterator.hasNext();
            preVisitMapEntry(child, childKey, value, isLast);
            visitMapEntry(child, childKey, value, isLast);
            postVisitMapEntry(child, childKey, value, isLast);
        }
        postVisitMap(context, map);
    }

    @Override
    public void visitList(Context context, List<Object> list) {
        preVisitList(context, list);
        for (int i = 0; i < list.size(); i++) {
            boolean isLastItem = i == list.size() - 1;
            Object o = list.get(i);
            String childKey = "[" + i + "]";
            Context child = context.child(childKey, NodeKind.LIST);
            preVisitListItem(child, o, isLastItem);
            visitListItem(child, o, isLastItem);
            postVisitListItem(child, o, isLastItem);
        }
        postVisitList(context, list);
    }

    @Override
    public void visitString(Context context, String value) {
        visitObject(context, value);
    }

    @Override
    public void visitNumber(Context context, Number value) {
        visitObject(context, value);
    }

    @Override
    public void visit(Context context, Object object) {
        switch (ModelVisitor.kindOf(object)) {
            case MAP:
                visitMap(context, (Map<String, Object>) object);
                break;
            case LIST:
                visitList(context, (List<Object>) object);
                break;
            case STRING:
                visitString(context, (String) object);
                break;
            case NUMBER:
                visitNumber(context, (Number) object);
                break;
            case OBJECT:
                visitObject(context, object);
                break;
        }
    }

    public void visit() {
        visitMap(new Context(null, "", NodeKind.OBJECT), model);
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public StringBuilder append(Object obj) {
        return sb.append(obj);
    }

    public StringBuilder append(String str) {
        return sb.append(str);
    }

    public StringBuilder append(StringBuffer sb) {
        return this.sb.append(sb);
    }

    public StringBuilder append(CharSequence s) {
        return sb.append(s);
    }

    public StringBuilder append(boolean b) {
        return sb.append(b);
    }

    public StringBuilder append(char c) {
        return sb.append(c);
    }

    public StringBuilder append(int i) {
        return sb.append(i);
    }

    public StringBuilder append(long lng) {
        return sb.append(lng);
    }

    public StringBuilder append(float f) {
        return sb.append(f);
    }

    public StringBuilder append(double d) {
        return sb.append(d);
    }

    public boolean isEmpty() {
        return sb.length() == 0;
    }
}
