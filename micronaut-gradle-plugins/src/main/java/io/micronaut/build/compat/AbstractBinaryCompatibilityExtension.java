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

import org.gradle.api.provider.Provider;

public abstract class AbstractBinaryCompatibilityExtension implements BinaryCompatibibilityExtension {
    private final Provider<String> projectVersion;

    public AbstractBinaryCompatibilityExtension(Provider<String> projectVersion) {
        this.projectVersion = projectVersion;
    }

    @Override
    public void enabledAfter(String releaseVersion) {
        getEnabled().set(projectVersion.map(version -> {
            String[] tokens = releaseVersion.split("[.]");
            if (tokens.length != 3) {
                throw new IllegalArgumentException("Release version must consist of 3 parts: major.minor.bugfix but was: " + releaseVersion);
            }
            if (version.indexOf("-") > 0) {
                version = version.substring(0, version.indexOf("-"));
            }
            String[] projectVersionTokens = version.split("[.]");
            if (projectVersionTokens.length != 3) {
                throw new IllegalArgumentException("Project version must consist of 3 parts: major.minor.bugfix but was: " + version);
            }
            int projectMajor = Integer.parseInt(projectVersionTokens[0]);
            int projectMinor = Integer.parseInt(projectVersionTokens[1]);
            int projectBugfix = Integer.parseInt(projectVersionTokens[2]);
            int releaseMajor = Integer.parseInt(tokens[0]);
            int releaseMinor = Integer.parseInt(tokens[1]);
            int releaseBugfix = Integer.parseInt(tokens[2]);
            return (projectMajor > releaseMajor)
                || (projectMajor == releaseMajor && projectMinor > releaseMinor)
                || (projectMajor == releaseMajor && projectMinor == releaseMinor && projectBugfix > releaseBugfix);
        }));


    }
}
