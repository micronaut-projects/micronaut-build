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

public final class PomDependency {
    private final boolean isManaged;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String scope;

    public PomDependency(boolean isManaged, String groupId, String artifactId, String version, String scope) {
        this.isManaged = isManaged;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
    }

    public boolean isManaged() {
        return isManaged;
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

    public String getScope() {
        return scope;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version + (!scope.isEmpty() ? " scope " + scope : "");
    }

    public boolean isImport() {
        return "import".equals(scope);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PomDependency that = (PomDependency) o;

        if (isManaged != that.isManaged) {
            return false;
        }
        if (!groupId.equals(that.groupId)) {
            return false;
        }
        if (!artifactId.equals(that.artifactId)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }
        return scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        int result = (isManaged ? 1 : 0);
        result = 31 * result + groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + scope.hashCode();
        return result;
    }
}
