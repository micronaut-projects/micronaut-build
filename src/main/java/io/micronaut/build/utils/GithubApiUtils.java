/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.utils;

import org.gradle.api.GradleException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import org.gradle.api.logging.Logger;

public final class GithubApiUtils {
    private static final String GH_TOKEN_PUBLIC_REPOS_READONLY = "GH_TOKEN_PUBLIC_REPOS_READONLY";
    private static final String GH_USERNAME = "GH_USERNAME";

    private GithubApiUtils() {
    }

    public static byte[] fetchReleasesFromGitHub(Logger logger, String slug) {
        String url = "https://api.github.com/repos/" + normalizeSlug(slug) + "/releases";
        try {
            return fetchFromGithub(connectionForGithubUrl(logger, url));
        } catch (IOException ex) {
            throw new GradleException("Failed to read releases from " + url, ex);
        }
    }

    public static byte[] fetchTagsFromGitHub(Logger logger, String slug) {
        String url = "https://api.github.com/repos/" + normalizeSlug(slug) + "/tags";
        try {
            return fetchFromGithub(connectionForGithubUrl(logger, url));
        } catch (IOException ex) {
            throw new GradleException("Failed to read tags from " + url, ex);
        }
    }

    private static byte[] fetchFromGithub(HttpURLConnection con) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ReadableByteChannel rbc = Channels.newChannel(con.getInputStream()); WritableByteChannel wbc=Channels.newChannel(out)){
            ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
            while (rbc.read(buffer) != -1) {
                buffer.flip();
                wbc.write(buffer);
                buffer.compact();
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                wbc.write(buffer);
            }
            return out.toByteArray();
        }
    }

    /**
     * @see <a href="https://docs.github.com/en/rest/overview/other-authentication-methods?apiVersion=2022-11-28#basic-authentication">Github REST API Basic Authentication</a>
     * @param logger Gradle Logger
     * @param githubUrl Github API URL
     * @return A HTTP URL connection
     * @throws IOException Exception thrown while building the HTTP URL Connection.
     */
    private static HttpURLConnection connectionForGithubUrl(Logger logger, String githubUrl) throws IOException {
        URL url = new URL(githubUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/vnd.github.v3+json");
        if (System.getenv(GH_TOKEN_PUBLIC_REPOS_READONLY) != null || System.getenv(GH_USERNAME) != null) {
            con.setRequestProperty("Authorization", BasicAuthUtils.basicAuth(System.getenv(GH_USERNAME), System.getenv(GH_TOKEN_PUBLIC_REPOS_READONLY)));
        } else {
            if (logger != null) {
                if (System.getenv(GH_USERNAME) == null) {
                    logger.warn("Environment variable " + GH_USERNAME + " not defined");
                }
                if (System.getenv(GH_TOKEN_PUBLIC_REPOS_READONLY) == null) {
                    logger.warn("Environment variable " + GH_TOKEN_PUBLIC_REPOS_READONLY + " not defined");
                }
            }
        }
        return con;
    }

    private static String normalizeSlug(String slug) {
        if (slug.startsWith("/")) {
            slug = slug.substring(1);
        }
        if (slug.endsWith("/")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug;
    }
}
