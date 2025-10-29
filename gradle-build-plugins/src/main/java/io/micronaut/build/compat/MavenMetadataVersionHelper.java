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

import io.micronaut.build.utils.ComparableVersion;
import io.micronaut.build.utils.VersionParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class MavenMetadataVersionHelper {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)([.-]\\w+)?$");
    private static final String VERSION_OPEN_TAG = "<version>";
    private static final String VERSION_CLOSE_TAG = "</version>";

    private MavenMetadataVersionHelper() {

    }

    public static List<ComparableVersion> findReleasesFrom(byte[] mavenMetadata) {
        List<String> allVersions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(new String(mavenMetadata, StandardCharsets.UTF_8)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(VERSION_OPEN_TAG)) {
                    String version = line.substring(line.indexOf(VERSION_OPEN_TAG) + VERSION_OPEN_TAG.length(), line.indexOf(VERSION_CLOSE_TAG));
                    allVersions.add(version);
                }
            }
            return allVersions.stream()
                .map(VersionParser::parse)
                .sorted()
                .collect(Collectors.toList());

        } catch (IOException e) {
            return List.of();
        }
    }

    public static Optional<ComparableVersion> findPreviousReleaseFor(ComparableVersion version, List<ComparableVersion> releases) {
        return releases.stream()
            .filter(v -> v.qualifier().isEmpty())
            .filter(v -> v.compareTo(version) < 0)
            .reduce((a, b) -> b);
    }
}
