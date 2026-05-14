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
import java.util.Set;
import java.util.stream.Collectors;

public class InternalAnnotationPostProcessRule implements PostProcessViolationsRule {
    @Override
    public void execute(ViolationCheckContextWithViolations context) {
        Set<String> internalTypes = context.getUserData(InternalAnnotationCollectorRule.INTERNAL_TYPES);
        if (internalTypes != null) {
            Set<String> toBeSuppressed = context.getViolations().keySet().stream().filter(internalTypes::contains).collect(Collectors.toSet());
            Set<Map.Entry<String, List<Violation>>> entries = context.getViolations().entrySet();
            for (Map.Entry<String, List<Violation>> entry : entries) {
                if (toBeSuppressed.contains(entry.getKey())) {
                    List<Violation> replacement = entry.getValue().stream().map(v -> {
                        if (v.getSeverity() == Severity.error) {
                            return v.withSeverity(Severity.warning);
                        }
                        return v;
                    }).collect(Collectors.toList());
                    entry.getValue().clear();
                    entry.getValue().addAll(replacement);
                }
            }
        }
    }
}
