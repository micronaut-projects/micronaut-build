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

import io.micronaut.build.utils.ExternalURLService;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

@CacheableTask
public abstract class FindBaselineTask extends DefaultTask {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final int CACHE_IN_SECONDS = 3600;

    @Input
    public abstract Property<String> getBaseRepository();

    @Input
    public abstract Property<String> getGroupId();

    @Input
    public abstract Property<String> getArtifactId();

    @Input
    public abstract Property<String> getCurrentVersion();

    @Input
    protected Provider<Long> getTimestamp() {
        return getProviders().provider(() -> {
            long seconds = System.currentTimeMillis() / 1000;
            long base = seconds / CACHE_IN_SECONDS;
            return base * CACHE_IN_SECONDS;
        });
    }

    @Internal
    protected Provider<byte[]> getMavenMetadata() {
        Provider<String> artifactPath = getGroupId().zip(getArtifactId(), (groupId, artifactId) -> groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml");
        return getBaseRepository().zip(artifactPath, (baseUrl, path) -> {
            String url = baseUrl + "/" + path ;
            try {
                return getDownloader().get().fetchFromURL(new URI(url)).orElse(EMPTY_BYTE_ARRAY);
            } catch (URISyntaxException e) {
                throw new GradleException("Invalid URI: " + url, e);
            }
        });
    }

    @Internal
    abstract Property<ExternalURLService> getDownloader();

    @Inject
    protected abstract ProviderFactory getProviders();

    @OutputFile
    public abstract RegularFileProperty getPreviousVersion();

    @TaskAction
    public void execute() throws IOException {
        byte[] metadata = getMavenMetadata().get();
        List<VersionModel> releases = MavenMetadataVersionHelper.findReleasesFrom(metadata);
        VersionModel current = VersionModel.of(trimVersion());
        Optional<VersionModel> previous = MavenMetadataVersionHelper.findPreviousReleaseFor(current, releases);
        if (!previous.isPresent()) {
            throw new IllegalStateException("Could not find a previous version for " + current);
        }
        Files.write(getPreviousVersion().get().getAsFile().toPath(), previous.get().toString().getBytes("UTF-8"));
    }

    private String trimVersion() {
        String version = getCurrentVersion().get();
        int idx = version.indexOf('-');
        if (idx >= 0) {
            return version.substring(0, idx);
        }
        return version;
    }
}
