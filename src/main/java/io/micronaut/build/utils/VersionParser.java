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
package io.micronaut.build.utils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class VersionParser {
    public static Pattern SEMANTIC_VERSION = Pattern.compile("(?<major>\\d+)[.]?((?<minor>\\d+)([.]?(?<patch>\\d+)(?<extra>([.]\\d+)+)?)?)?[-.]?((?<qualifier>\\p{Alpha}+)(-?(?<qualifierVersion>\\d+))?)?");

    public static ComparableVersion parse(String version) {
        try {
            var matcher = SEMANTIC_VERSION.matcher(version);
            if (matcher.find()) {
                var major = matcher.group("major");
                var minor = matcher.group("minor");
                var patch = matcher.group("patch");
                var qualifier = matcher.group("qualifier");
                var qualifierVersion = matcher.group("qualifierVersion");
                var extraVersion = matcher.group("extra");
                return new ComparableVersion(
                    version,
                    asInt(major),
                    asInt(minor),
                    asInt(patch),
                    qualifier,
                    asInt(qualifierVersion),
                    asList(extraVersion)
                );
            } else {
                return new ComparableVersion(version, null, null, null, null, null, List.of());
            }
        } catch (Exception ex) {
            // safety net
            return new ComparableVersion(version, null, null, null, null, null, List.of());
        }
    }

    private static Integer asInt(String value) {
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private static List<Integer> asList(String extraVersions) {
        if (extraVersions == null) {
            return List.of();
        }
        return Arrays.stream(extraVersions.split("[.]"))
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .toList();
    }
}
