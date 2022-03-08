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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.micronaut.build.compat.AcceptanceHelper.formatAcceptance;

public class AcceptedApiChangesRule implements ViolationTransformer {
    public static final String CHANGES_FILE = "changesFile";

    private final Map<String, List<AcceptedApiChange>> changes;

    public AcceptedApiChangesRule(Map<String, String> params) {
        String filePath = params.get(CHANGES_FILE);
        File changesFile = new File(filePath);
        if (changesFile.exists()) {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                this.changes = AcceptedApiChangesParser.parse(fis)
                        .stream()
                        .collect(Collectors.groupingBy(AcceptedApiChange::getType));
            } catch (IOException e) {
                throw new GradleException("Unable to parse accepted regressions file", e);
            }
        } else {
            this.changes = Collections.emptyMap();
        }
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
