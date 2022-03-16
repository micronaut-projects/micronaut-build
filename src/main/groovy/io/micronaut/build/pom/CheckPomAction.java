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

import groovy.json.JsonGenerator;
import groovy.json.JsonOutput;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * This work action is responsible for checking the contents of a POM file.
 * It will verify that:
 * <ul>
 *     <li>All dependencies declared in the POM file exist in a repository</li>
 *     <li>If it's a BOM file, verify that all the dependencies it talks about belong to the same group as the BOM itself</li>
 *     <li>If it's a BOM file, make sure that it doesn't import other BOMs</li>
 * </ul>
 */
public abstract class CheckPomAction implements WorkAction<CheckPomAction.Parameters> {
    interface Parameters extends WorkParameters {
        Property<String> getDependencyPath();

        ListProperty<String> getRepositories();

        Property<String> getGroupId();

        Property<String> getArtifactId();

        Property<String> getVersion();

        RegularFileProperty getPomFile();

        RegularFileProperty getReportFile();

        DirectoryProperty getPomDirectory();
    }

    @Override
    public void execute() {
        String dependencyPath = getParameters().getDependencyPath().get();
        File pomFile = getParameters().getPomFile().getAsFile().get();
        File reportFile = getParameters().getReportFile().getAsFile().get();
        String groupId = getParameters().getGroupId().get();
        String artifactId = getParameters().getArtifactId().get();
        String version = getParameters().getVersion().get();
        File pomDirectory = getParameters().getPomDirectory().getAsFile().get();
        List<String> repositories = getParameters().getRepositories().get();
        PomDownloader downloader = new PomDownloader(repositories, pomDirectory);
        PomParser parser = new PomParser(downloader);
        PomFile pom = parser.parse(pomFile, groupId, artifactId, version);
        Map<String, String> foundDependencies = Collections.synchronizedMap(new LinkedHashMap<>());
        Set<String> missingDependencies = Collections.synchronizedSet(new LinkedHashSet<>());
        pom.getDependencies().parallelStream().forEach(dependency -> {
            String key = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
            Optional<File> downloadedFile = downloader.tryDownloadPom(dependency);
            if (downloadedFile.isPresent()) {
                foundDependencies.put(key, downloadedFile.get().getAbsolutePath());
            } else {
                missingDependencies.add(key);
            }
        });
        PomValidation validation = new PomValidation(dependencyPath, pom, foundDependencies, missingDependencies);
        JsonGenerator generator = new JsonGenerator.Options()
                .excludeFieldsByName("import", "importingBom")
                .build();
        String json = JsonOutput.prettyPrint(generator.toJson(validation));
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), "UTF-8")) {
            writer.write(json);
        } catch (IOException e) {
            throw new GradleException("Unable to write report", e);
        }
    }

}
