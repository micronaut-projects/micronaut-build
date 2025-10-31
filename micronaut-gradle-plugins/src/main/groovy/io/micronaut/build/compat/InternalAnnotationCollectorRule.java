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

import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiHasAnnotations;
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule;
import me.champeau.gradle.japicmp.report.Violation;

import java.util.HashSet;

public class InternalAnnotationCollectorRule extends AbstractContextAwareViolationRule {
    public static final String INTERNAL_TYPES = "micronaut.internal.types";

    @Override
    public Violation maybeViolation(JApiCompatibility member) {
        if (member instanceof JApiClass) {
            JApiClass jApiClass = (JApiClass) member;
            maybeRecord((JApiHasAnnotations) member, jApiClass.getFullyQualifiedName());
        }
        return null;
    }

    private void maybeRecord(JApiHasAnnotations member, String name) {
        if (isAnnotatedWithInternal(member)) {
            HashSet<String> types = getContext().getUserData(INTERNAL_TYPES);
            if (types == null) {
                types = new HashSet<>();
                getContext().putUserData(INTERNAL_TYPES, types);
            }
            types.add(name);
        }
    }

    static boolean isAnnotatedWithInternal(JApiHasAnnotations member) {
        return member.getAnnotations()
                .stream()
                .anyMatch(ann -> ann.getFullyQualifiedName().equals("io.micronaut.core.annotation.Internal"));
    }
}
