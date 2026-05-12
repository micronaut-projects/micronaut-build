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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PomDownloader {
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("<snapshot>.*</snapshot>");
    private static final Pattern ID_PATTERN = Pattern.compile("<timestamp>(.*?)</timestamp><buildNumber>(.*?)</buildNumber>");
    private static final int DEFAULT_MAX_REMOTE_DOWNLOADS = 4;
    private static final int DEFAULT_MAX_ATTEMPTS = 6;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = (int) Duration.ofSeconds(30).toMillis();
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = (int) Duration.ofSeconds(60).toMillis();
    private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 1_000L;
    private static final long DEFAULT_MAX_BACKOFF_MILLIS = 30_000L;
    private static final String USER_AGENT = "micronaut-build-pom-checker";
    private static final Semaphore REMOTE_DOWNLOADS = new Semaphore(intProperty(
        "micronaut.pom.checker.max.remote.downloads",
        DEFAULT_MAX_REMOTE_DOWNLOADS
    ));

    private final List<String> repositories;
    private final File pomsDirectory;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<String> processing = new HashSet<>();
    private final int maxAttempts = intProperty("micronaut.pom.checker.download.max.attempts", DEFAULT_MAX_ATTEMPTS);
    private final int connectTimeoutMillis = intProperty("micronaut.pom.checker.connect.timeout.millis", DEFAULT_CONNECT_TIMEOUT_MILLIS);
    private final int readTimeoutMillis = intProperty("micronaut.pom.checker.read.timeout.millis", DEFAULT_READ_TIMEOUT_MILLIS);

    public PomDownloader(List<String> repositories, File pomDirectory) {
        this.repositories = repositories;
        this.pomsDirectory = pomDirectory;
    }

    public Optional<File> tryDownloadPom(PomDependency dependency) {
        TransientDownloadException lastTransientFailure = null;
        for (String repositoryUrl : repositories) {
            try {
                Optional<File> pom = tryDownloadPom(dependency, repositoryUrl);
                if (pom.isPresent()) {
                    return pom;
                }
            } catch (TransientDownloadException e) {
                lastTransientFailure = e;
            }
        }
        if (lastTransientFailure != null) {
            throw new GradleException(lastTransientFailure.getMessage(), lastTransientFailure);
        }
        return Optional.empty();
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
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while waiting to download POM at " + uri, e);
        } finally {
            lock.unlock();
        }
        try {
            File pomFile = new File(pomsDirectory, pomFilePath);
            if (pomFile.exists() && isSnapshot) {
                Files.delete(pomFile.toPath());
            }
            if (!pomFile.exists()) {
                DownloadResult result = download(uri, pomFile);
                if (result == DownloadResult.NOT_FOUND) {
                    return Optional.empty();
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
        File metadataFile = new File(pomsDirectory, basedir + "maven-metadata.xml");
        try {
            DownloadResult result = download(uri, metadataFile);
            if (result == DownloadResult.NOT_FOUND) {
                return Optional.empty();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(metadataFile.toPath())))) {
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

    private DownloadResult download(String uri, File destination) throws IOException {
        URI parsedUri = toUri(uri);
        if (isLocalFile(parsedUri)) {
            File source = toLocalFile(parsedUri);
            if (!source.isFile()) {
                return DownloadResult.NOT_FOUND;
            }
            copyAtomically(source, destination);
            return DownloadResult.DOWNLOADED;
        }

        URL url = parsedUri.toURL();
        IOException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpURLConnection connection = null;
            REMOTE_DOWNLOADS.acquireUninterruptibly();
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(connectTimeoutMillis);
                connection.setReadTimeout(readTimeoutMillis);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/xml,text/xml,*/*;q=0.8");
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    return DownloadResult.NOT_FOUND;
                }
                if (status >= 200 && status < 300) {
                    try (InputStream in = connection.getInputStream()) {
                        copyAtomically(in, destination);
                    }
                    return DownloadResult.DOWNLOADED;
                }
                if (isTransientStatus(status)) {
                    sleepBeforeRetry(uri, attempt, retryAfterMillis(connection), "HTTP " + status);
                    continue;
                }
                return DownloadResult.NOT_FOUND;
            } catch (IOException e) {
                lastException = e;
                sleepBeforeRetry(uri, attempt, Optional.empty(), e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                REMOTE_DOWNLOADS.release();
            }
        }
        throw new TransientDownloadException("Unable to download POM at " + uri + " after " + maxAttempts + " attempts" +
            (lastException == null ? "" : ": " + lastException.getMessage()), lastException);
    }

    private void sleepBeforeRetry(String uri, int attempt, Optional<Long> retryAfterMillis, String reason) {
        if (attempt >= maxAttempts) {
            return;
        }
        long backoff = retryAfterMillis.orElseGet(() -> exponentialBackoffWithJitter(attempt));
        System.err.println("Retrying POM download after " + reason + " (attempt " + attempt + "/" + maxAttempts + "): " + uri);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while downloading POM at " + uri, e);
        }
    }

    private static long exponentialBackoffWithJitter(int attempt) {
        long exponential = DEFAULT_INITIAL_BACKOFF_MILLIS << Math.min(attempt - 1, 5);
        long capped = Math.min(exponential, DEFAULT_MAX_BACKOFF_MILLIS);
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, capped / 4));
        return capped + jitter;
    }

    private static Optional<Long> retryAfterMillis(HttpURLConnection connection) {
        String retryAfter = connection.getHeaderField("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Optional.of(Math.max(0L, seconds * 1_000L));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isTransientStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static URI toUri(String uri) {
        try {
            URI parsed = new URI(uri);
            if (parsed.getScheme() == null) {
                return new File(uri).toURI();
            }
            return parsed;
        } catch (URISyntaxException e) {
            return new File(uri).toURI();
        } catch (IllegalArgumentException e) {
            throw new GradleException("Invalid repository URI " + uri, e);
        }
    }

    private static boolean isLocalFile(URI uri) {
        String scheme = uri.getScheme();
        return scheme == null || "file".equalsIgnoreCase(scheme);
    }

    private static File toLocalFile(URI uri) {
        if (uri.getScheme() == null) {
            return new File(uri.getPath());
        }
        return new File(uri);
    }

    private static void copyAtomically(File source, File destination) throws IOException {
        try (InputStream in = Files.newInputStream(source.toPath())) {
            copyAtomically(in, destination);
        }
    }

    private static void copyAtomically(InputStream input, File destination) throws IOException {
        File parent = destination.getParentFile();
        Files.createDirectories(parent.toPath());
        File temporary = File.createTempFile(destination.getName(), ".tmp", parent);
        try {
            Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temporary.toPath());
            throw e;
        }
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            String environmentName = name.toUpperCase(Locale.US).replace('.', '_');
            value = System.getenv(environmentName);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private enum DownloadResult {
        DOWNLOADED,
        NOT_FOUND
    }

    private static final class TransientDownloadException extends RuntimeException {
        private TransientDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
