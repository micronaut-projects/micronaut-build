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
package io.micronaut.docs;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.extension.JavaExtensionRegistry;
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry;

import java.util.HashMap;

public class DocsExtensionRegistry implements ExtensionRegistry {
    @Override
    public void register(Asciidoctor asciidoctor) {
        JavaExtensionRegistry javaExtensionRegistry = asciidoctor.javaExtensionRegistry();
        javaExtensionRegistry.inlineMacro("mnapi", MicronautApiMacro.class);
        javaExtensionRegistry.inlineMacro("api", ApiMacro.class);
        javaExtensionRegistry.inlineMacro("ann", AnnotationMacro.class);
        javaExtensionRegistry.inlineMacro("pkg", PackageMacro.class);
        javaExtensionRegistry.inlineMacro("jdk", JdkApiMacro.class);
        javaExtensionRegistry.inlineMacro("jee", JeeApiMacro.class);
        javaExtensionRegistry.inlineMacro("rs", ReactiveStreamsApiMacro.class);
        javaExtensionRegistry.inlineMacro("rx", RxJavaApiMacro.class);
        javaExtensionRegistry.inlineMacro("reactor", ReactorJavaApiMacro.class);
        javaExtensionRegistry.inlineMacro("dependency", BuildDependencyMacro.class);
        javaExtensionRegistry.blockMacro(new LanguageSnippetMacro("snippet", new HashMap<>(), asciidoctor));
        javaExtensionRegistry.block(new ConfigurationPropertiesMacro(asciidoctor));
    }
}
