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

import japicmp.model.BridgeModifier;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiMethod;
import japicmp.model.JApiModifier;
import japicmp.model.SyntheticModifier;
import me.champeau.gradle.japicmp.report.Severity;
import me.champeau.gradle.japicmp.report.Violation;
import me.champeau.gradle.japicmp.report.ViolationTransformer;

import java.util.Optional;

/**
 * This rule turns errors on internal Java bridge methods into warnings
 * and ignores synthetic classes.
 */
public class JavaBridgeAndSyntheticRule implements ViolationTransformer {

    @Override
    public Optional<Violation> transform(String type, Violation violation) {
        JApiCompatibility violationMember = violation.getMember();
        if (violationMember instanceof JApiMethod) {
            JApiMethod method = (JApiMethod) violationMember;
            JApiModifier<BridgeModifier> bridgeModifier = method.getBridgeModifier();
            if (bridgeModifier != null) {
                return Optional.of(violation.withSeverity(Severity.warning));
            }
        }
        if (violationMember instanceof JApiClass) {
            JApiClass clazz = (JApiClass) violationMember;
            JApiModifier<SyntheticModifier> bridgeModifier = clazz.getSyntheticModifier();
            if (bridgeModifier != null) {
                return Optional.of(violation.withSeverity(Severity.warning));
            }
        }
        return Optional.of(violation);
    }

}
