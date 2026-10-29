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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A build service that caches URLs responses and makes sure that we don't call
 * the same server concurrently.
 */
public abstract class ExternalURLService implements BuildService<BuildServiceParameters.None> {
    private static final Logger LOGGER = Logging.getLogger(ExternalURLService.class);
    private static final int TIMEOUT_MILLIS = 30_000;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<String> currentHosts = new HashSet<>();

    private final Map<URI, byte[]> responses = new ConcurrentHashMap<>();

    public Optional<byte[]> fetchFromURL(URI uri) {
        return withHostLock(uri, () -> Optional.ofNullable(responses.computeIfAbsent(uri, Downloader::doDownload)));
    }

    /**
     * Fetches the contents of the given URI, retrying transient failures.
     * Unlike {@link #fetchFromURL(URI)}, a failure is not silently turned into
     * an empty result: an empty optional means that the resource does not
     * exist (HTTP 404 or 410, or a missing file), while any other failure
     * (connection errors, timeouts, 5xx or other unexpected responses) is retried up to
     * {@code maxAttempts} times, waiting {@code initialBackoff} before the
     * second attempt and doubling the wait after every further failure.
     * If every attempt fails, a {@link GradleException} naming the URI and the
     * last failure is thrown.
     *
     * @param uri the URI to fetch
     * @param maxAttempts the maximum number of attempts, at least 1
     * @param initialBackoff the wait before the first retry
     * @return the contents, or an empty optional if the resource does not exist
     */
    public Optional<byte[]> fetchFromURL(URI uri, int maxAttempts, Duration initialBackoff) {
        int attempts = Math.max(1, maxAttempts);
        byte[] cached = responses.get(uri);
        if (cached != null) {
            return Optional.of(cached);
        }
        return withHostLock(uri, () -> {
            byte[] downloaded = responses.get(uri);
            if (downloaded != null) {
                return Optional.of(downloaded);
            }
            long backoff = Math.max(0, initialBackoff.toMillis());
            IOException lastFailure = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    Optional<byte[]> result = download(uri);
                    result.ifPresent(bytes -> responses.put(uri, bytes));
                    return result;
                } catch (IOException e) {
                    lastFailure = e;
                    if (attempt < attempts) {
                        LOGGER.warn("Attempt {} of {} to download {} failed: {}. Retrying in {} ms", attempt, attempts, uri, e.toString(), backoff);
                        sleep(uri, backoff);
                        backoff *= 2;
                    }
                }
            }
            throw new GradleException("Unable to download " + uri + " after " + attempts + " attempt(s): " + lastFailure, lastFailure);
        });
    }

    private static void sleep(URI uri, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while waiting to retry the download of " + uri, e);
        }
    }

    private static Optional<byte[]> download(URI uri) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        if (connection instanceof HttpURLConnection http) {
            int status = http.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
                http.disconnect();
                return Optional.empty();
            }
            if (status != HttpURLConnection.HTTP_OK) {
                http.disconnect();
                throw new IOException("Server returned HTTP status " + status);
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (InputStream stream = connection.getInputStream()) {
            stream.transferTo(outputStream);
        } catch (FileNotFoundException e) {
            if (connection instanceof HttpURLConnection) {
                throw e;
            }
            return Optional.empty();
        }
        return Optional.of(outputStream.toByteArray());
    }

    private <T> T withHostLock(URI uri, Supplier<T> action) {
        lock.lock();
        String host = uri.getHost();
        try {
            while (currentHosts.contains(host)) {
                condition.await();
            }
            currentHosts.add(host);
        } catch (InterruptedException e) {
            throw new GradleException("Unable to fetch external resource at " + uri, e);
        } finally {
            lock.unlock();
        }
        try {
            return action.get();
        } finally {
            lock.lock();
            try {
                currentHosts.remove(host);
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static Provider<ExternalURLService> registerOn(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent("ExternalURLService", ExternalURLService.class, spec -> {
        });
    }
}
