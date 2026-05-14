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
package io.micronaut.docs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class DefaultDocFileOperations implements DocFileOperations {

    @Override
    public void mkdir(File directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.createDirectories(directory.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void copy(File destinationDirectory, CopySource... sources) {
        if (destinationDirectory == null || sources == null) {
            return;
        }
        mkdir(destinationDirectory);
        for (CopySource source : sources) {
            copySource(destinationDirectory.toPath(), source);
        }
    }

    @Override
    public void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            Path path = file.toPath();
            if (Files.isDirectory(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(child);
                    }
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copySource(Path destinationDirectory, CopySource source) {
        if (source == null || source.source() == null || !source.source().exists()) {
            return;
        }
        Path sourcePath = source.source().toPath();
        try {
            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> paths = Files.walk(sourcePath)) {
                    for (Path file : paths.filter(Files::isRegularFile).toList()) {
                        Path relativePath = sourcePath.relativize(file);
                        if (included(relativePath, source)) {
                            copyFile(file, destinationDirectory.resolve(relativePath));
                        }
                    }
                }
            } else if (included(sourcePath.getFileName(), source)) {
                copyFile(sourcePath, destinationDirectory.resolve(sourcePath.getFileName()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean included(Path relativePath, CopySource source) {
        if (!source.hasIncludes()) {
            return true;
        }
        String normalizedPath = relativePath.toString().replace(File.separatorChar, '/');
        String fileName = relativePath.getFileName().toString();
        return source.includes().contains(normalizedPath) || source.includes().contains(fileName);
    }

    private static void copyFile(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
