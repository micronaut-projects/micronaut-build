/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.catalogs.internal;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum Status {
    INTEGRATION("snapshot", "[a-f0-9]{6,}"),
    ALPHA("alpha", "a"),
    BETA("beta", "b"),
    MILESTONE("milestone", "m"),
    RELEASE_CANDIDATE("rc", "cr"),
    RELEASE();

    private final Pattern regex;

    Status(String... qualifiers) {
        if (qualifiers.length == 0) {
            this.regex = null;
        } else {
            this.regex = Pattern.compile("(?i).*[.-](?:" + Arrays.stream(qualifiers).map(Pattern::quote).map(p -> "(?:" + p + ")").collect(Collectors.joining("|")) + ")[.\\d-+]*");
        }
    }

    public boolean isAsStableOrMoreStableThan(Status other) {
        return ordinal() >= other.ordinal();
    }

    public static Status detectStatus(String version) {
        for (Status status : values()) {
            Pattern regex = status.regex;
            if (regex != null && regex.matcher(version).find()) {
                return status;
            }
        }
        return RELEASE;
    }
}
