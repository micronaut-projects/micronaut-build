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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.workers.WorkParameters;

/**
 * The parameters of the isolated SonarLint analysis.
 */
public interface SonarLintWorkParameters extends WorkParameters {

    DirectoryProperty getBaseDir();

    ListProperty<String> getMainFiles();

    ListProperty<String> getTestFiles();

    ConfigurableFileCollection getAnalyzers();

    /**
     * The rules file, or absent for the bundled Micronaut Profile.
     */
    RegularFileProperty getRulesFile();

    SetProperty<String> getSkippedRules();

    MapProperty<String, String> getAnalysisProperties();

    DirectoryProperty getWorkDir();

    /**
     * Where the raw issues are written, as JSON.
     */
    RegularFileProperty getIssuesFile();
}
