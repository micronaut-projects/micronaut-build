/*
 * Copyright 2017 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.macros;

import io.micronaut.docs.javadoc.JvmLibrary;
import io.micronaut.docs.javadoc.Micronaut;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.extension.InlineMacroProcessor;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class ApiMacro extends InlineMacroProcessor {

    public ApiMacro(String macroName) {
        super(macroName);
    }

    public ApiMacro(String macroName, Map<String, Object> config) {
        super(macroName, config);
    }

    @Override
    public PhraseNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        int methodIndex = target.lastIndexOf('(');
        int propIndex = target.lastIndexOf('#');
        String methodRef = "";
        String propRef = "";
        String shortName;
        if (methodIndex > -1 && target.endsWith(")")) {
            String sig = target.substring(methodIndex + 1, target.length() - 1);
            target = target.substring(0, methodIndex);
            methodIndex = target.lastIndexOf('.');
            if (methodIndex > -1) {
                String sigRef = "-" + String.join("-", sig.split(",")) + "-";
                String methodName = target.substring(methodIndex + 1);

                methodRef = "#" + methodName + sigRef;
                target = target.substring(0, methodIndex);
                int classIndex = target.lastIndexOf('.');
                if (classIndex > -1) {
                    shortName = target.substring(classIndex + 1) + "." + methodName + "(" + sig + ")";
                } else {
                    shortName = target;
                }
            } else {
                return null;
            }
        } else if (propIndex > -1) {
            propRef = target.substring(propIndex);
            target = target.substring(0, propIndex);
            shortName = propRef.substring(1);
        } else {
            int classIndex = target.lastIndexOf('.');
            shortName = classIndex > -1 ? target.substring(classIndex + 1) : target;
        }

        JvmLibrary lib = getJvmLibrary(attributes);
        String baseUri;
        try {
            baseUri = getBaseUri(parent.getDocument().getAttributes(), getAttributeKey(), lib);
        } catch (Exception e) {
            baseUri = getBaseUri(Collections.emptyMap(), getAttributeKey(), lib);
        }
        String module = target.startsWith("java") ? "java.base" : null;
        Object moduleAttribute = attributes.get("module");
        if (moduleAttribute != null) {
            module = moduleAttribute.toString();
        }
        if (module != null && !module.isBlank()) {
            baseUri = baseUri + "/" + module;
        }

        Object text = attributes.get("text");
        if (text != null) {
            shortName = text.toString();
        }

        Map<String, Object> options = inlineAnchorOptions(baseUri, target, methodRef, propRef, lib);
        return createPhraseNode(parent, "anchor", formatShortName(shortName), attributes, options);
    }

    public JvmLibrary getJvmLibrary(Map<String, Object> attributes) {
        Object packagePrefix = attributes.get("packagePrefix");
        Object defaultUri = attributes.get("defaultUri");
        JvmLibrary library = getJvmLibrary();
        if (packagePrefix != null || defaultUri != null) {
            return new JvmLibrary() {
                @Override
                public String defaultUri() {
                    return defaultUri != null ? defaultUri.toString() : library.defaultUri();
                }

                @Override
                public String getDefaultPackagePrefix() {
                    return packagePrefix != null ? packagePrefix.toString() : library.getDefaultPackagePrefix();
                }
            };
        }
        return library;
    }

    public static Map<String, Object> inlineAnchorOptions(
        String baseUri,
        String target,
        String methodRef,
        String propRef,
        JvmLibrary jvmLibrary
    ) {
        return Map.of(
            "type", ":link",
            "target", (baseUri + "/" + targetPathUrl(target, jvmLibrary) + ".html" + methodRef + propRef).replace('$', '.')
        );
    }

    public static String targetPathUrl(String target, JvmLibrary jvmLibrary) {
        String defaultPackage = jvmLibrary.getDefaultPackagePrefix();
        String result = target;
        if (defaultPackage != null && !target.startsWith(defaultPackage)) {
            result = defaultPackage + target;
        }
        return scapeDots(result);
    }

    public static String scapeDots(String str) {
        StringBuilder result = new StringBuilder();
        String[] arr = str.split("\\.");
        for (int i = 0; i < arr.length; i++) {
            String token = arr[i];
            result.append(token);
            if (token.isEmpty()) {
                throw new StringIndexOutOfBoundsException("Invalid class string: " + str);
            }
            if (Character.isUpperCase(token.charAt(0))) {
                if (i != arr.length - 1) {
                    result.append(".");
                }
            } else {
                result.append("/");
            }
        }
        return result.toString();
    }

    protected String formatShortName(String shortName) {
        return shortName;
    }

    public static String getBaseUri(Map<String, Object> attrs, String attributeKey, JvmLibrary jvmLibrary) {
        if (attributeKey != null) {
            Object baseUri = attrs.get(attributeKey);
            if (baseUri == null) {
                baseUri = attrs.get(attributeKey.toLowerCase(Locale.ROOT));
            }
            if (baseUri != null) {
                return baseUri.toString();
            }
        }
        return jvmLibrary.defaultUri();
    }

    public String getAttributeKey() {
        return null;
    }

    public JvmLibrary getJvmLibrary() {
        return new Micronaut();
    }
}
