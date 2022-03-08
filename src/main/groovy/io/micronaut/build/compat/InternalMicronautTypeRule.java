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

import japicmp.model.JApiHasAnnotations;
import me.champeau.gradle.japicmp.report.Severity;
import me.champeau.gradle.japicmp.report.Violation;
import me.champeau.gradle.japicmp.report.ViolationTransformer;

import java.util.Optional;

import static io.micronaut.build.compat.InternalAnnotationCollectorRule.isAnnotatedWithInternal;

/**
 * This rule turns errors on internal types into warnings.
 */
public class InternalMicronautTypeRule implements ViolationTransformer {
    private static boolean isInternalType(String className) {
        return className.startsWith("io.micronaut") && className.contains(".internal.");
    }

    /**
     * Transforms the current violation.
     *
     * @param type the type on which the violation was found
     * @param violation the violation
     * @return a transformed violation. If the violation should be suppressed, return Optional.empty()
     */
    @Override
    public Optional<Violation> transform(String type, Violation violation) {
        if (isInternalType(type) && violation.getSeverity() == Severity.error) {
            return Optional.of(violation.withSeverity(Severity.warning));
        }
        if (violation.getMember() instanceof JApiHasAnnotations) {
            JApiHasAnnotations member = (JApiHasAnnotations) violation.getMember();
            if (isAnnotatedWithInternal(member)) {
                return Optional.of(violation.withSeverity(Severity.warning));
            }
        }
        return Optional.of(violation);
    }

}
