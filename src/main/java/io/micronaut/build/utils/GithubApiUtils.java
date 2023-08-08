/*
 * Copyright 2017-2023 original authors
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
import org.gradle.api.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public final class GithubApiUtils {
    private static final String GH_TOKEN_PUBLIC_REPOS_READONLY = "GH_TOKEN_PUBLIC_REPOS_READONLY";
    private static final String GH_USERNAME = "GH_USERNAME";

    public static final String GITHUB_API_BASE_URL_SYSTEM_PROPERTY = "github.api.base.url";
    public static final String GITHUB_BASE_API_URL = "https://api.github.com";

    private GithubApiUtils() {
    }

    static byte[] fetchTagsFromGitHub(Logger logger, String slug) {
        return fetchFromGithub(logger, slug, "tags");
    }

    private static byte[] fetchFromGithub(Logger logger, String slug, String what) {
        String url = System.getProperty(GITHUB_API_BASE_URL_SYSTEM_PROPERTY, GITHUB_BASE_API_URL) + "/repos/" + normalizeSlug(slug) + "/" + what;
        logger.lifecycle("Fetching " + what + " from " + url);
        try {
            return fetchFromGithub(logger, connectionForGithubUrl(logger, url));
        } catch (IOException ex) {
            throw new GradleException("Failed to read " + what + " from " + url, ex);
        }
    }

    private static byte[] fetchFromGithub(Logger logger, HttpURLConnection con) throws IOException {
        try (InputStream in = con.getInputStream()){
            return readFromStream(in).toByteArray();
        } catch (IOException ex) {
            ByteArrayOutputStream errorOut = readFromStream(con.getErrorStream());
            logger.error("Failed to read from Github API. Response code: " + con.getResponseCode() +
                         "\nResponse message: " + con.getResponseMessage() +
                         "\nError body: " + errorOut +
                         "\nResponse headers: " + con.getHeaderFields());
            throw ex;
        }
    }

    private static ByteArrayOutputStream readFromStream(InputStream in) throws IOException {
        ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
        try (ReadableByteChannel rbc = Channels.newChannel(in); WritableByteChannel wbc=Channels.newChannel(errorOut)){
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
        }
        return errorOut;
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
        if (System.getenv("CI") != null) {
            con.setRequestProperty("User-Agent", "Micronaut-Framework-Ci");
        }
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
