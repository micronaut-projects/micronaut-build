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
package io.micronaut.docs

import org.asciidoctor.extension.*
import org.asciidoctor.ast.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ApiMacro extends InlineMacroProcessor {

    ApiMacro(String macroName) {
        super(macroName)
    }

    ApiMacro(String macroName, Map<String, Object> config) {
        super(macroName, config)
    }

    @Override
    Object process(ContentNode parent, String target, Map<String, Object> attributes) {
        // is it a method reference
        int methodIndex = target.lastIndexOf('(')
        int propIndex = target.lastIndexOf('#')
        String methodRef = ""
        String propRef = ""
        String shortName
        if (methodIndex > -1 && target.endsWith(")")) {
            String sig = target.substring(methodIndex + 1, target.length() - 1)
            target = target.substring(0, methodIndex)
            methodIndex = target.lastIndexOf('.')
            if (methodIndex > -1) {
                String sigRef = "-${sig.split(',').join('-')}-"
                String methodName = target.substring(methodIndex + 1, target.length())

                methodRef = "#${methodName}${sigRef}"
                target = target.substring(0, methodIndex)
                int classIndex = target.lastIndexOf('.')
                if (classIndex > -1) {
                    shortName = "${target.substring(classIndex + 1, target.length())}.${methodName}(${sig})"
                } else {
                    shortName = target
                }
            } else {
                return null
            }
        } else {
            if (propIndex > -1) {
                propRef = target.substring(propIndex, target.length())
                target = target.substring(0, propIndex)
                shortName = propRef.substring(1)
            } else {

                int classIndex = target.lastIndexOf('.')
                if (classIndex > -1) {
                    shortName = target.substring(classIndex + 1, target.length())
                } else {
                    shortName = target
                }
            }
        }


        JvmLibrary lib = getJvmLibrary(attributes)
        String baseUri
        try {
            baseUri = getBaseUri(parent.document.attributes, getAttributeKey(), lib)
        } catch (e) {
            baseUri = getBaseUri(Collections.emptyMap(), getAttributeKey(), lib)
        }
        String module = target.startsWith("java") ? "java.base" : null
        if (attributes.module) {
            module = attributes.module
        }
        if (module) {
            baseUri = "${baseUri}/${module}"
        }

        if (attributes.text) {
            shortName = attributes.text
        }

        Map<String, Object> options = inlineAnchorOptions(baseUri, target, methodRef, propRef, lib)
        // Prepend twitterHandle with @ as text link.
        return createPhraseNode(parent, "anchor", formatShortName(shortName), attributes, options)
    }

    JvmLibrary getJvmLibrary(Map<String, Object> attributes) {
        var packagePrefix = attributes['packagePrefix']
        var defaultUri = attributes['defaultUri']
        var library = getJvmLibrary()
        if (packagePrefix != null || defaultUri != null) {
            return new JvmLibrary() {
                @Override
                String defaultUri() {
                    return defaultUri != null ? defaultUri : library.defaultUri()
                }

                @Override
                String getDefaultPackagePrefix() {
                    return packagePrefix != null ? packagePrefix : library.defaultPackagePrefix
                }
            }
        }
        return library
    }

    static Map<String, Object> inlineAnchorOptions(String baseUri, String target, String methodRef, String propRef, JvmLibrary jvmLibrary) {
        [
                type: ':link',
                target: "${baseUri}/${targetPathUrl(target, jvmLibrary)}.html${methodRef}${propRef}".toString().replaceAll('\\$', '.')
        ] as Map<String, Object>
    }


    static String targetPathUrl(String target, JvmLibrary jvmLibrary) {
        String defaultPackage = jvmLibrary.getDefaultPackagePrefix()
        String result = target
        if (defaultPackage != null && !target.startsWith(defaultPackage)) {
            result = "${defaultPackage}${target}"
        }
        scapeDots(result)
    }

    static String scapeDots(String str) {
        String result = ""
        String[] arr = str.split("\\.")
        for (int i = 0; i < arr.length; i++) {
            String token = arr[i]
            result += token
            if (token.length() == 0) {
                throw new StringIndexOutOfBoundsException("Invalid class string: " + str)
            }
            if (Character.isUpperCase(token.charAt(0))) {
                if (i != arr.length - 1) {
                    result += "."
                }
            } else {
                result += "/"
            }
        }
        result
    }

    protected String formatShortName(String shortName) {
        return shortName
    }


    static String getBaseUri(Map<String, Object> attrs, String attributeKey, JvmLibrary jvmLibrary) {
        (attributeKey && attrs[attributeKey]) ? attrs[attributeKey].toString() : jvmLibrary.defaultUri()
    }


    String getAttributeKey() {
        return null
    }


    JvmLibrary getJvmLibrary() {
        return new Micronaut()
    }
}
