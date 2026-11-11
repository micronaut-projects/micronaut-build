/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.build.sonarlint;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configures the {@code sonarLint} task: an offline analysis with SonarSource's Java analyzer and the
 * rules of a SonarCloud quality profile. Available as {@code micronautBuild.sonarLint}.
 */
public abstract class SonarLintExtension {

    /**
     * The rule the local analyzer raises on every {@code Foo<?>} return type, while SonarCloud never raises it
     * on Micronaut projects.
     */
    public static final String S1452 = "java:S1452";
    public static final String S1452_REASON = "SonarCloud never raises it on Micronaut projects, while the local "
        + "sonar-java raises it on every Foo<?> return type, including unchanged code";

    public static final Set<String> DEFAULT_GATE_TYPES = Set.of("BUG", "VULNERABILITY");
    public static final Set<String> DEFAULT_GATE_SEVERITIES = Set.of("BLOCKER", "CRITICAL");

    @SuppressWarnings("java:S5993") // Gradle instantiates the extension
    public SonarLintExtension() {
        getEnabled().convention(false);
        getAllFiles().convention(false);
        getIncludeTests().convention(true);
        getFailOnIssues().convention(true);
        getShowAllIssues().convention(true);
        getGateTypes().convention(DEFAULT_GATE_TYPES);
        getGateSeverities().convention(DEFAULT_GATE_SEVERITIES);
        getSkippedRules().convention(Map.of(S1452, S1452_REASON));
        getExcludes().convention(List.of());
    }

    /**
     * Whether {@code check} depends on {@code sonarLint}. Defaults to {@code false}; the task can always be
     * run explicitly.
     */
    public abstract Property<Boolean> getEnabled();

    /**
     * The git ref the changed files and lines are computed against, from its merge base with the working tree.
     * Defaults to the {@code sonarLint.baseRef} Gradle property, else {@code origin/HEAD}, the remote's default
     * branch.
     */
    public abstract Property<String> getBaseRef();

    /**
     * Analyses every source file and reports every issue, instead of the changed files and lines only.
     * Defaults to the {@code sonarLint.all} Gradle property, else {@code false}.
     */
    public abstract Property<Boolean> getAllFiles();

    /**
     * Whether the test sources are analysed too, as SonarCloud does. Defaults to {@code true}.
     */
    public abstract Property<Boolean> getIncludeTests();

    /**
     * Whether the task fails when a gate-failing issue is reported. Defaults to {@code true}.
     */
    public abstract Property<Boolean> getFailOnIssues();

    /**
     * Whether issues that do not fail the gate are printed too. Defaults to {@code true}; they are always in
     * the reports.
     */
    public abstract Property<Boolean> getShowAllIssues();

    /**
     * The issue types that fail the gate. Defaults to BUG and VULNERABILITY, as the Micronaut Quality Gate.
     */
    public abstract SetProperty<String> getGateTypes();

    /**
     * The severities that fail the gate, whatever the type. Defaults to BLOCKER and CRITICAL.
     */
    public abstract SetProperty<String> getGateSeverities();

    /**
     * A rules file exported from a SonarCloud quality profile (see the {@code sonarLintExportRules} task).
     * Defaults to the Micronaut Profile bundled with the plugin.
     */
    public abstract RegularFileProperty getRulesFile();

    /**
     * Rules of the profile that are not enabled locally, with the reason. Defaults to {@code java:S1452}.
     */
    public abstract MapProperty<String, String> getSkippedRules();

    /**
     * Glob patterns, relative to the root project directory, of source files that are not analysed
     * (the equivalent of {@code sonar.exclusions}).
     */
    public abstract ListProperty<String> getExcludes();
}
