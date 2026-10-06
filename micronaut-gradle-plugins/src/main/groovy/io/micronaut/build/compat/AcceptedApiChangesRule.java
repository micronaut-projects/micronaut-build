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

import me.champeau.gradle.japicmp.report.Violation;
import me.champeau.gradle.japicmp.report.ViolationTransformer;
import org.gradle.api.GradleException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.micronaut.build.compat.AcceptanceHelper.formatAcceptance;

public class AcceptedApiChangesRule implements ViolationTransformer {
    /**
     * The path of the accepted changes file. A relative path is resolved against the
     * working directory of the japicmp worker process, which is not the project directory.
     */
    public static final String CHANGES_FILE = "changesFile";
    /**
     * The contents of the accepted changes file, which, unlike an absolute path, keep the
     * task relocatable.
     */
    public static final String CHANGES = "changes";

    private final Map<String, List<AcceptedApiChange>> changes;

    public AcceptedApiChangesRule(Map<String, String> params) {
        String json = params.get(CHANGES);
        String filePath = params.get(CHANGES_FILE);
        if (json != null && !json.isBlank()) {
            this.changes = parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        } else if (filePath != null && new File(filePath).exists()) {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                this.changes = parse(fis);
            } catch (IOException e) {
                throw new GradleException("Unable to parse accepted regressions file", e);
            }
        } else {
            this.changes = Collections.emptyMap();
        }
    }

    private static Map<String, List<AcceptedApiChange>> parse(InputStream changes) {
        return AcceptedApiChangesParser.parse(changes)
                .stream()
                .collect(Collectors.groupingBy(AcceptedApiChange::getType));
    }

    @Override
    public Optional<Violation> transform(String type, Violation violation) {
        List<AcceptedApiChange> apiChanges = changes.get(type);
        String violationDescription = Violation.describe(violation.getMember());
        if (apiChanges != null) {
            Optional<AcceptedApiChange> any = apiChanges.stream()
                    .filter(c -> c.matches(type, violationDescription))
                    .findAny();
            if (any.isPresent()) {
                return Optional.of(violation.acceptWithDescription(any.get().getReason()));
            }
        }
        switch (violation.getSeverity()) {
            case info:
            case accepted:
            case warning:
                return Optional.of(violation);
            default:
                return Optional.of(violation.withDescription(
                    violation.getHumanExplanation() + formatAcceptance(type, violationDescription))
            );
        }
    }
}
