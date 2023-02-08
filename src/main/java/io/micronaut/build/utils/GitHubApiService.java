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
package io.micronaut.build.utils;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.HashMap;
import java.util.Map;

/**
 * A build service that caches GitHub API responses and makes sure that we don't call
 * the GitHub API concurrently.
 */
public abstract class GitHubApiService implements BuildService<BuildServiceParameters.None> {
    private static final Logger LOGGER = Logging.getLogger(GitHubApiService.class);

    private final Map<String, byte[]> releases = new HashMap<>();
    private final Map<String, byte[]> tags = new HashMap<>();

    public byte[] fetchReleasesFromGitHub(String slug) {
        synchronized (releases) {
            return releases.computeIfAbsent(slug, s -> GithubApiUtils.fetchReleasesFromGitHub(LOGGER, slug));
        }
    }

    public byte[] fetchTagsFromGitHub(String slug) {
        synchronized (tags) {
            return tags.computeIfAbsent(slug, s -> GithubApiUtils.fetchTagsFromGitHub(LOGGER, slug));
        }
    }

    public static Provider<GitHubApiService> registerOn(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent("GitHubService", GitHubApiService.class, spec -> {});
    }
}
