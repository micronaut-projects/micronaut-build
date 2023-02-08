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

import org.gradle.api.GradleException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PomDownloader {
    private static Pattern SNAPSHOT_PATTERN = Pattern.compile("<snapshot>.*</snapshot>");
    private static Pattern ID_PATTERN = Pattern.compile("<timestamp>(.*?)</timestamp><buildNumber>(.*?)</buildNumber>");

    private final List<String> repositories;
    private final File pomsDirectory;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<String> processing = new HashSet<>();

    public PomDownloader(List<String> repositories, File pomDirectory) {
        this.repositories = repositories;
        this.pomsDirectory = pomDirectory;
    }

    public Optional<File> tryDownloadPom(PomDependency dependency) {
        return repositories.stream()
                .map(repositoryUrl -> tryDownloadPom(dependency, repositoryUrl))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Optional<File> tryDownloadPom(PomDependency dependency, String repositoryUrl) {
        if (repositoryUrl.endsWith("/")) {
            repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 1);
        }
        String group = dependency.getGroupId();
        String artifact = dependency.getArtifactId();
        String version = dependency.getVersion();
        String basedir = "/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/";
        boolean isSnapshot = version.endsWith("-SNAPSHOT");
        if (isSnapshot) {
            Optional<String> snapshotVersion = findSnapshotVersion(repositoryUrl, basedir);
            if (snapshotVersion.isPresent()) {
                version = version.substring(0, version.indexOf("-SNAPSHOT")) + "-" + snapshotVersion.get();
            }
        }
        String pomFilePath = basedir + artifact + "-" + version + ".pom";
        String uri = repositoryUrl + pomFilePath;
        lock.lock();
        try {
            // Multiple threads can be trying to download and copy the POM file
            // concurrently, so we need to make them wait if another thread is
            // already processing the same POM file.
            while (processing.contains(pomFilePath)) {
                condition.await();
            }
            processing.add(pomFilePath);
        } catch (InterruptedException e) {
            throw new GradleException("Unable to download POM at "+ uri, e);
        } finally {
            lock.unlock();
        }
        try {
            URL url = new URL(uri);
            File pomFile = new File(pomsDirectory, pomFilePath);
            if (pomFile.exists() && isSnapshot) {
                pomFile.delete();
            }
            if (!pomFile.exists()) {
                try (InputStream in = url.openStream()) {
                    pomFile.getParentFile().mkdirs();
                    Files.copy(in, pomFile.toPath());
                }
            }
            return Optional.of(pomFile);
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            lock.lock();
            try {
                processing.remove(pomFilePath);
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private Optional<String> findSnapshotVersion(String repositoryUrl, String basedir) {
        String uri = repositoryUrl + basedir + "maven-metadata.xml";
        try {
            URL url = new URL(uri);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String text = sb.toString().replaceAll("[\\r\\n\\t ]", "");
                Matcher matcher = SNAPSHOT_PATTERN.matcher(text);
                if (matcher.find()) {
                    text = matcher.group();
                    matcher = ID_PATTERN.matcher(text);
                    if (matcher.find()) {
                        return Optional.of(matcher.group(1) + "-" + matcher.group(2));
                    }
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
