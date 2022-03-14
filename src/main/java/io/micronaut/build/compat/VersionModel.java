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
package io.micronaut.build.compat;

import org.jetbrains.annotations.NotNull;

public class VersionModel implements Comparable<VersionModel> {
    private final String current;
    private final int currentAsInt;
    private final VersionModel leaf;

    public static VersionModel of(String version) {
        int idx = version.indexOf(".");
        if (idx < 0) {
            return new VersionModel(version, null);
        }
        return new VersionModel(version.substring(0, idx), of(version.substring(idx + 1)));
    }

    private VersionModel(String current, VersionModel leaf) {
        this.current = current;
        this.currentAsInt = parseInt(current);
        this.leaf = leaf;
    }

    private static int parseInt(String current) {
        try {
            return Integer.parseInt(current);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int compareTo(@NotNull VersionModel o) {
        int result = Integer.compare(currentAsInt, o.currentAsInt);
        if (result != 0) {
            return result;
        }
        if (leaf == null && o.leaf == null) {
            return 0;
        }
        if (leaf == null) {
            return -1;
        }
        if (o.leaf == null) {
            return 1;
        }
        return leaf.compareTo(o.leaf);
    }

    public String getCurrent() {
        return current;
    }

    public int getCurrentAsInt() {
        return currentAsInt;
    }
    
    public String getVersion() {
        if (leaf == null) {
            return current;
        }
        return current + "." + leaf;
    }

    @Override
    public String toString() {
        return getVersion();
    }
}
