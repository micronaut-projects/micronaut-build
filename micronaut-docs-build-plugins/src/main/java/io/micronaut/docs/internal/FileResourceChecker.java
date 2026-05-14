package io.micronaut.docs.internal;

import java.io.File;

/**
 * Simple class that checks whether a path relative to a base directory exists
 * or not. Each instance of the class can have its own base directory.
 */
public class FileResourceChecker {
    private final File baseDir;

    public FileResourceChecker(File baseDir) {
        this.baseDir = baseDir;
    }

    public boolean exists(String path) {
        return new File(baseDir, path).exists();
    }
}
