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

import org.gradle.api.Action;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.provider.Property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class InterceptedVersionCatalogBuilder implements VersionCatalogBuilder {
    private final VersionCatalogBuilder delegate;
    private final Map<String, String> versions = new HashMap<>();

    private final List<Consumer<? super LibraryDefinition>> onLibrary = new ArrayList<>();

    public InterceptedVersionCatalogBuilder(VersionCatalogBuilder delegate) {
        this.delegate = delegate;
    }

    public void onLibrary(Consumer<? super LibraryDefinition> consumer) {
        onLibrary.add(consumer);
    }

    @Override
    public Property<String> getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void from(Object dependencyNotation) {
        delegate.from(dependencyNotation);
    }

    @Override
    public String version(String alias, Action<? super MutableVersionConstraint> versionSpec) {
        return delegate.version(alias, versionSpec);
    }

    @Override
    public String version(String alias, String version) {
        versions.put(alias, version);
        return delegate.version(alias, version);
    }

    @Override
    public LibraryAliasBuilder library(String alias, String group, String artifact) {
        var library = delegate.library(alias, group, artifact);
        return new LibraryAliasBuilder() {
            @Override
            public void version(Action<? super MutableVersionConstraint> versionSpec) {
                library.version(versionSpec);
            }

            @Override
            public void version(String version) {
                library.version(version);
            }

            @Override
            public void versionRef(String versionRef) {
                library.versionRef(versionRef);
                onLibrary.forEach(listener -> {
                    listener.accept(new LibraryDefinition(group, artifact, versionRef));
                });
            }

            @Override
            public void withoutVersion() {
                library.withoutVersion();
                onLibrary.forEach(listener -> listener.accept(new LibraryDefinition(group, artifact, null)));
            }
        };
    }

    @Override
    public void library(String alias, String groupArtifactVersion) {
        delegate.library(alias, groupArtifactVersion);
    }

    @Override
    public PluginAliasBuilder plugin(String alias, String id) {
        return delegate.plugin(alias, id);
    }

    @Override
    public void bundle(String alias, List<String> aliases) {
        delegate.bundle(alias, aliases);
    }

    @Override
    public String getLibrariesExtensionName() {
        return delegate.getLibrariesExtensionName();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    public final class LibraryDefinition {
        private final String groupId;
        private final String artifactId;
        private final String versionRef;

        public LibraryDefinition(
            String groupId,
            String artifactId,
            String versionRef
        ) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.versionRef = versionRef;
        }

        public String version() {
                if (versionRef == null) {
                    return null;
                }
                return versions.get(versionRef);
            }

        public String groupId() {
            return groupId;
        }

        public String artifactId() {
            return artifactId;
        }

        public String versionRef() {
            return versionRef;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (LibraryDefinition) obj;
            return Objects.equals(this.groupId, that.groupId) &&
                   Objects.equals(this.artifactId, that.artifactId) &&
                   Objects.equals(this.versionRef, that.versionRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId, versionRef);
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version();
        }

        }
}
