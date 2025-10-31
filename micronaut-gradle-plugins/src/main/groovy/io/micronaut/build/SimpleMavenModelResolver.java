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
package io.micronaut.build;

import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.File;

class SimpleMavenModelResolver implements ModelResolver {
    private final ConfigurationContainer configurations;
    private final DependencyHandler dependencies;

    SimpleMavenModelResolver(ConfigurationContainer configurations, DependencyHandler dependencies) {
        this.configurations = configurations;
        this.dependencies = dependencies;
    }

    private File resolvePomFile(String groupId, String artifactId, String version, String classifier) {
        var conf = configurations.detachedConfiguration();
        var classy = classifier == null ? "" : (":" + classifier);
        conf.getDependencies().add(dependencies.create(groupId + ":" + artifactId + ":" + version + classy + "@pom"));
        return conf.getSingleFile();
    }

    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
        return new FileModelSource(resolvePomFile(groupId, artifactId, version, null));
    }

    @Override
    public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    @Override
    public ModelSource resolveModel(org.apache.maven.model.Dependency dependency) throws UnresolvableModelException {
        return new FileModelSource(
            resolvePomFile(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getClassifier())
        );
    }

    @Override
    public void addRepository(Repository repository) throws InvalidRepositoryException {

    }

    @Override
    public void addRepository(Repository repository, boolean b) throws InvalidRepositoryException {

    }

    @Override
    public ModelResolver newCopy() {
        return this;
    }
}
