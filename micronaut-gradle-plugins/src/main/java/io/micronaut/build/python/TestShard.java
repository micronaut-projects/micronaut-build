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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;

/**
 * One shard of a test suite split across several CI jobs: shard {@code index} of {@code count}
 * (both 1-based) runs the test classes whose name hashes to it.
 * <p>
 * Classes are assigned by the name of their top level class, so nested and anonymous classes
 * always run with the class declaring them, and the assignment only depends on the class name:
 * every shard computes the same partition without coordinating.
 *
 * @param index the 1-based index of this shard
 * @param count the number of shards
 */
record TestShard(int index, int count) implements Spec<FileTreeElement> {

    private static final String CLASS_SUFFIX = ".class";

    TestShard {
        if (count < 1) {
            throw new InvalidUserDataException("The number of test shards must be at least 1, got " + count);
        }
        if (index < 1 || index > count) {
            throw new InvalidUserDataException("The test shard index must be between 1 and " + count + ", got " + index);
        }
    }

    /**
     * Parses the {@code <index>/<count>} form of the {@code python-ci-shard} property, for example
     * {@code 2/4} for the second of four shards.
     *
     * @param value the property value
     * @return the shard
     */
    static TestShard parse(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length != 2) {
            throw invalid(value);
        }
        try {
            return new TestShard(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            throw invalid(value);
        }
    }

    private static InvalidUserDataException invalid(String value) {
        return new InvalidUserDataException("The " + MicronautPythonPlugin.PYTHON_CI_SHARD_PROPERTY
            + " property must have the form <index>/<count>, for example 2/4, got '" + value + "'");
    }

    /**
     * Whether an element of a test classes directory belongs to this shard. Directories always do,
     * so that the exclude spec built from this shard never prunes a directory.
     *
     * @param element the class file or directory
     * @return whether the element is part of this shard
     */
    @Override
    public boolean isSatisfiedBy(FileTreeElement element) {
        return element.isDirectory() || includesClassFile(element.getRelativePath().getPathString());
    }

    /**
     * Whether the class file at the given path, relative to its classes directory, belongs to this shard.
     *
     * @param relativePath the path of the class file, for example {@code io/micronaut/FooSpec$Inner.class}
     * @return whether the class belongs to this shard
     */
    boolean includesClassFile(String relativePath) {
        if (count == 1) {
            return true;
        }
        String name = relativePath.endsWith(CLASS_SUFFIX)
            ? relativePath.substring(0, relativePath.length() - CLASS_SUFFIX.length())
            : relativePath;
        int nested = name.indexOf('$', name.lastIndexOf('/') + 1);
        if (nested >= 0) {
            name = name.substring(0, nested);
        }
        // String.hashCode is specified by the JLS, so every JVM computes the same partition
        return Math.floorMod(name.hashCode(), count) == index - 1;
    }

    @Override
    public String toString() {
        return index + "/" + count;
    }
}
