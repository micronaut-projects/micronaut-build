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

import me.champeau.gradle.igp.GitIncludeExtension;
import me.champeau.gradle.igp.IncludedGitRepo;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Utilities which may be used by the plugins, but also from
 * users in case of manual configuration.
 */
public class IncludedBuildSupport {

    /**
     * Configures an included build to create their version catalog. This is done so that
     * the catalog can be read by the main build in its settings.gradle file. Without this,
     * the build may fail because Gradle will not automatically trigger the creation of the
     * catalog.
     * The task takes care of snapshotting the gradle/libs.versions.toml file found in the
     * included build directory, and will only perform execution of the task if the snapshot
     * changed.
     *
     * @param execOperations the exec operations
     * @param evt the source included build event
     * @param gitIncludeExtension the git include extension
     * @param githubProjectName the name of the GitHub project which is included
     */
    public static void configureIncludedBuildCatalogPublication(ExecOperations execOperations,
                                                                IncludedGitRepo.CodeReadyEvent evt,
                                                                GitIncludeExtension gitIncludeExtension,
                                                                String githubProjectName) {
        var checkoutDirectory = evt.getCheckoutDirectory();
        var digestFile = gitIncludeExtension.getCheckoutsDirectory().file("catalog-" + githubProjectName + ".sha1").get().getAsFile().toPath();
        var catalogFile = new File(checkoutDirectory, "gradle/libs.versions.toml");
        if (catalogFile.exists()) {
            String newDigest;
            try {
                var previousDigest = Files.exists(digestFile) ? Files.readString(digestFile) : "";
                var digest = MessageDigest.getInstance("SHA-1");
                try (var stream = new DigestInputStream(new FileInputStream(catalogFile), digest)) {
                    byte[] bytes = new byte[1024];
                    while (stream.read(bytes) > 0) ;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                newDigest = toHex(digest.digest());
                Files.createDirectories(digestFile.getParent());
                Files.writeString(digestFile, newDigest);
                if (!newDigest.equals(previousDigest)) {
                    System.out.println("Catalog changed for " + githubProjectName + ": triggering build.");
                    execOperations.exec(spec -> {
                        spec.workingDir(checkoutDirectory);
                        spec.commandLine(System.getProperty("os.name").toLowerCase(Locale.US).contains("windows") ? "gradlew.bat" : "./gradlew");
                        spec.args("-q", "generateCatalogAsToml", "--no-daemon");
                    });
                } else {
                    System.out.println("Catalog unchanged for " + githubProjectName + ".");
                }
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String toHex(byte[] bytes) {
        var hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
