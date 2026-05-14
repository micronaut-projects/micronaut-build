/*
 * Copyright 2018 original authors
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

import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.extension.InlineMacroProcessor;

import java.util.Map;

/**
 * @author graemerocher
 * @since 1.0
 */
public class PackageMacro extends InlineMacroProcessor {
    public PackageMacro(String macroName) {
        super(macroName);
    }

    public PackageMacro(String macroName, Map<String, Object> config) {
        super(macroName, config);
    }

    @Override
    public PhraseNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String defaultPackage = getDefaultPackagePrefix();
        if (defaultPackage != null && !target.startsWith(defaultPackage)) {
            target = defaultPackage + target;
        }
        String baseUri = getBaseUri(parent.getDocument().getAttributes());
        Map<String, Object> options = Map.of(
            "type", ":link",
            "target", baseUri + "/" + target.replace('.', '/') + "/package-summary.html"
        );

        String pkg = target;
        Object text = attributes.get("text");
        if (text != null) {
            pkg = text.toString();
        }
        return createPhraseNode(parent, "anchor", pkg, attributes, options);
    }

    protected String getBaseUri(Map<String, Object> attrs) {
        return "../api";
    }

    protected String getDefaultPackagePrefix() {
        return "io.micronaut.";
    }
}
