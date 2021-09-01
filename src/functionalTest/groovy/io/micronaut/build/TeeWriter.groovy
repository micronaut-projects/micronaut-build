/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build

import groovy.transform.CompileStatic

@CompileStatic
class TeeWriter extends Writer {
    private final Writer one
    private final Writer two

    static TeeWriter of(Writer one, Writer two) {
        new TeeWriter(one, two)
    }

    private TeeWriter(Writer one, Writer two) {
        this.one = one
        this.two = two
    }

    @Override
    void write(int c) throws IOException {
        try {
            one.write(c)
        } finally {
            two.write(c)
        }
    }

    @Override
    void write(char[] cbuf) throws IOException {
        try {
            one.write(cbuf)
        } finally {
            two.write(cbuf)
        }
    }

    @Override
    void write(char[] cbuf, int off, int len) throws IOException {
        try {
            one.write(cbuf, off, len)
        } finally {
            two.write(cbuf, off, len)
        }
    }

    @Override
    void write(String str) throws IOException {
        try {
            one.write(str)
        } finally {
            two.write(str)
        }
    }

    @Override
    void write(String str, int off, int len) throws IOException {
        try {
            one.write(str, off, len)
        } finally {
            two.write(str, off, len)
        }
    }

    @Override
    void flush() throws IOException {
        try {
            one.flush()
        } finally {
            two.flush()
        }
    }

    @Override
    void close() throws IOException {
        try {
            one.close()
        } finally {
            two.close()
        }
    }
}
