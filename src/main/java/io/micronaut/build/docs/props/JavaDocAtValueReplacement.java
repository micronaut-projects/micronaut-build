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
package io.micronaut.build.docs.props;

import java.lang.reflect.Field;

public class JavaDocAtValueReplacement {
    private static final String OPEN_ATVALUE = "{@value ";
    private static final String CLOSE_ATVALUE = "}";

    static String replaceAtValue(String type, String description) {
        if(description == null || description.isEmpty()) {
            return description;
        }
        String atValue = atValue(type, description);
        if (atValue == null) {
            return description;
        }
        String result = description.substring(0, description.indexOf(OPEN_ATVALUE));
        result += atValue;
        String sub = description.substring(description.indexOf(atValue) + atValue.length());
        result += sub.substring(sub.indexOf(CLOSE_ATVALUE) + CLOSE_ATVALUE.length());
        return result;
    }

    public static String atValue(String type, String description) {
        AtValue atValue = atValueField(type, description);

        if (atValue != null && atValue.getType() != null && atValue.getFieldName() != null) {
            try {
                Class<?> clazz = Class.forName(atValue.getType());
                Field f = clazz.getField(atValue.getFieldName());
                Class<?> t = f.getType();
                if (t == int.class) {
                    return String.valueOf(f.getInt(null));
                } else if (t == double.class) {
                    return String.valueOf(f.getDouble(null));
                } else if (t == boolean.class) {
                    return String.valueOf(f.getBoolean(null));
                }
                return f.get(null).toString();
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {

            }
        }
        return null;
    }

    public static AtValue atValueField(String type, String description) {
        AtValue result = new AtValue();

        if(description.contains(OPEN_ATVALUE)) {
            String str = description.substring(description.indexOf(OPEN_ATVALUE) + OPEN_ATVALUE.length());
            if(str.contains(CLOSE_ATVALUE)) {
                str = str.substring(0, str.indexOf(CLOSE_ATVALUE));
            }
            if ( str.startsWith("#")) {
                str = str.substring(1);
                result.setType(type);
                result.setFieldName(str);
            } else if ( str.contains("#")) {
                result.setType(str.substring(0, str.indexOf('#')));
                result.setFieldName(str.substring(str.indexOf('#') + 1));
            }
        }
        return result;
    }
}
