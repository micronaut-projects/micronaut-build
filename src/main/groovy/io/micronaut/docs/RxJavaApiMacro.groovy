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

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class RxJavaApiMacro extends ApiMacro {

    /**
     * @param macroName The macro name
     * @param config    The configuration
     */
    RxJavaApiMacro(String macroName, Map<String, Object> config) {
        super(macroName, config);
    }

    @Override
    @Nullable
    String getAttributeKey() {
        "rxapi";
    }

    @Override
    @NonNull
    JvmLibrary getJvmLibrary() {
        new RxJava()
    }
}
