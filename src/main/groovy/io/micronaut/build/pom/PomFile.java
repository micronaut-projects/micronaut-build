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
package io.micronaut.build.pom;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PomFile {
    private final String groupId;
    private final String artifactId;
    private final String version;

    private final boolean isBom;
    private final List<PomDependency> dependencies;
    private final Map<String, String> properties;

    public PomFile(String groupId,
                   String artifactId,
                   String version,
                   boolean bom,
                   List<PomDependency> dependencies,
                   Map<String, String> properties) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.isBom = bom;
        this.dependencies = dependencies;
        this.properties = properties;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public boolean isBom() {
        return isBom;
    }

    public boolean isImportingBom() {
        return dependencies.stream()
                .anyMatch(PomDependency::isImport);
    }

    public List<PomDependency> findImports() {
        return dependencies.stream()
                .filter(PomDependency::isImport)
                .collect(Collectors.toList());
    }

    public List<PomDependency> getDependencies() {
        return dependencies;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "PomFile{" +
                "isBom=" + isBom +
                ", dependencies=" + dependencies +
                '}';
    }

}
