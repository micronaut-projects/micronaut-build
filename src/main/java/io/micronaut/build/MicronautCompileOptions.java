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
package io.micronaut.build;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.CompileOptions;

import javax.inject.Inject;

public abstract class MicronautCompileOptions {
    public abstract Property<Boolean> getFork();
    public abstract Property<String> getMaxMemory();

    @Inject
    public MicronautCompileOptions() {
        getFork().convention(true);
        getMaxMemory().convention("1048m");
    }

    void applyTo(CompileOptions compileOptions) {
        if (getFork().get()) {
            compileOptions.setFork(true);
            compileOptions.getForkOptions().setMemoryMaximumSize(getMaxMemory().get());
        }
    }
}
