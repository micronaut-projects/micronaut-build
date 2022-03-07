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

import me.champeau.gradle.japicmp.report.PostProcessViolationsRule;
import me.champeau.gradle.japicmp.report.Severity;
import me.champeau.gradle.japicmp.report.Violation;
import me.champeau.gradle.japicmp.report.ViolationCheckContextWithViolations;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This rule turns errors on internal types into warnings.
 */
public class InternalMicronautTypeRule implements PostProcessViolationsRule {
    @Override
    public void execute(ViolationCheckContextWithViolations context) {
        Map<String, List<Violation>> violations = context.getViolations();
        Map<String, List<Violation>> newViolations = violations.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                            String className = e.getKey();
                            return e.getValue().stream()
                                    .map(violation -> {
                                        if (isInternalType(className) && violation.getSeverity() == Severity.error) {
                                            return violation.withSeverity(Severity.warning);
                                        }
                                        return violation;
                                    })
                                    .collect(Collectors.toList());
                        })
                );
        context.getViolations().clear();
        context.getViolations().putAll(newViolations);
    }

    private static boolean isInternalType(String className) {
        return className.startsWith("io.micronaut") && className.contains(".internal.");
    }
}
