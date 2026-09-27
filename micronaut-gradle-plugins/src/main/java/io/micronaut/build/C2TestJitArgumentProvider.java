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
package io.micronaut.build;

import org.gradle.process.CommandLineArgumentProvider;

import java.util.List;

/**
 * Makes C2 the top tier JIT of a test JVM running on a JDK whose default is the Graal compiler,
 * such as Oracle GraalVM, which the CI builds run on. The Graal compiler spends more CPU compiling
 * than C2, which does not pay off in a short-lived test JVM. On other JDKs the arguments change
 * nothing.
 */
public final class C2TestJitArgumentProvider implements CommandLineArgumentProvider {

    /**
     * Setting this Gradle property to {@code true} keeps the default JIT of the JDK, for example for
     * tests running Truffle languages, which need the Graal compiler.
     */
    public static final String KEEP_GRAAL_JIT_PROPERTY = "micronaut.test.graal-jit";

    @Override
    public Iterable<String> asArguments() {
        // UseJVMCICompiler is experimental outside of JDKs with JVMCI enabled
        return List.of("-XX:+UnlockExperimentalVMOptions", "-XX:-UseJVMCICompiler");
    }
}
