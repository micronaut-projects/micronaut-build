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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class Downloader {
    public static byte[] doDownload(URI uri) {
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
}
