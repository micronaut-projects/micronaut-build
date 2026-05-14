package io.micronaut.docs.macros;

import java.util.Map;

public interface ValueAtAttributes {
    /**
     * Given a map such as ['text':'version="1.0.1", groupId="io.micronaut"']
     * for name = 'version' it returns '1.0.1'.
     */
    default String valueAtAttributes(String name, Map<String, Object> attributes) {
        Object textValue = attributes.get("text");
        if (textValue != null) {
            String text = textValue.toString();
            String prefix = name + "=\"";
            if (text.contains(prefix)) {
                String partial = text.substring(text.indexOf(prefix) + prefix.length());
                if (partial.contains("\"")) {
                    return partial.substring(0, partial.indexOf('"'));
                }
                return partial;
            }
        }
        Object value = attributes.get(name);
        return value == null ? null : value.toString();
    }
}
