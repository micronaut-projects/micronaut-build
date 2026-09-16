/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.build.python;

import org.gradle.api.provider.Property;

/**
 * Configuration of the Pyronaut (Python) compiler support, available under
 * {@code micronautBuild.python}.
 */
public abstract class MicronautPythonExtension {

    /**
     * The version of Micronaut core which provides the Pyronaut compiler
     * ({@code io.micronaut:micronaut-inject-python} and {@code io.micronaut:micronaut-context-python}).
     * <p>
     * Defaults to the {@code micronaut} version of the version catalog (or the {@code micronautVersion}
     * project property). When set to an empty string, no compiler dependency is added automatically and
     * the {@code pyronautCompiler} configuration must be populated explicitly, which is what Micronaut
     * core itself does since it builds the compiler.
     *
     * @return the Micronaut core version providing the compiler
     */
    public abstract Property<String> getCompilerVersion();

    /**
     * Whether the {@code Test} tasks of this project run. Python tests need a GraalVM runtime
     * and are slow, so by convention they only run when the {@code python-ci} Gradle property
     * is set, which the dedicated "Python CI" GitHub workflow does.
     *
     * @return whether Python tests run in this build
     */
    public abstract Property<Boolean> getTestsEnabled();
}
