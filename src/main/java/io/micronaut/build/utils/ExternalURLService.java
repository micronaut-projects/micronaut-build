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
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A build service that caches URLs responses and makes sure that we don't call
 * the same server concurrently.
 */
public abstract class ExternalURLService implements BuildService<BuildServiceParameters.None> {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<String> currentHosts = new HashSet<>();

    private final Map<URI, byte[]> responses = new ConcurrentHashMap<>();

    public Optional<byte[]> fetchFromURL(URI uri) {
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
            return Optional.ofNullable(responses.computeIfAbsent(uri, ExternalURLService::doDownload));
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

    private static byte[] doDownload(URI uri) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (InputStream stream = uri.toURL().openStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            return null;
        }
        return outputStream.toByteArray();
    }

    public static Provider<ExternalURLService> registerOn(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent("ExternalURLService", ExternalURLService.class, spec -> {
        });
    }
}
