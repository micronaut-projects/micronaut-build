/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.build.sonarlint;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An issue the {@code sonarLint} task reports.
 *
 * @param file the file, relative to the root project directory
 * @param line the start line, or 0 for a file-level issue
 * @param column the start column, from 1
 * @param endLine the end line
 * @param endColumn the end column
 * @param rule the rule key
 * @param type the type: BUG, VULNERABILITY, CODE_SMELL or SECURITY_HOTSPOT
 * @param severity the severity in the quality profile
 * @param message the message
 * @param gateFailing whether the issue fails the gate
 */
record SonarLintIssue(String file, int line, int column, int endLine, int endColumn, String rule, String type,
                      String severity, String message, boolean gateFailing) {

    static final String GATE_FAILING = "gateFailing";
    private static final String KEY_FILE = "file";
    private static final String KEY_LINE = "line";
    private static final String KEY_COLUMN = "column";
    private static final String KEY_END_LINE = "endLine";
    private static final String KEY_END_COLUMN = "endColumn";
    private static final String KEY_RULE = "rule";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SEVERITY = "severity";
    private static final String KEY_MESSAGE = "message";

    /**
     * Reads an issue the analysis worker wrote.
     */
    static SonarLintIssue of(Map<String, Object> raw, Path root, Set<String> gateTypes, Set<String> gateSeverities) {
        String type = (String) raw.get(KEY_TYPE);
        String severity = (String) raw.get(KEY_SEVERITY);
        Path file = Path.of((String) raw.get(KEY_FILE)).toAbsolutePath().normalize();
        return new SonarLintIssue(
            root.relativize(file).toString().replace(File.separatorChar, '/'),
            number(raw, KEY_LINE),
            number(raw, KEY_COLUMN),
            number(raw, KEY_END_LINE),
            number(raw, KEY_END_COLUMN),
            (String) raw.get(KEY_RULE),
            type,
            severity,
            (String) raw.get(KEY_MESSAGE),
            gateTypes.contains(type) || gateSeverities.contains(severity));
    }

    private static int number(Map<String, Object> raw, String key) {
        return ((Number) raw.get(key)).intValue();
    }

    /**
     * The console line: {@code sonarlint: <file>:<line>:<column>: <SEVERITY> <TYPE> <rule> <message>}.
     */
    String format() {
        return SonarLintTask.PREFIX + file + ':' + line + ':' + column + ": " + severity + ' ' + type + ' ' + rule + ' '
            + message.replace('\n', ' ');
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(KEY_FILE, file);
        m.put(KEY_LINE, line);
        m.put(KEY_COLUMN, column);
        m.put(KEY_END_LINE, endLine);
        m.put(KEY_END_COLUMN, endColumn);
        m.put(KEY_RULE, rule);
        m.put(KEY_TYPE, type);
        m.put(KEY_SEVERITY, severity);
        m.put(KEY_MESSAGE, message);
        m.put(GATE_FAILING, gateFailing);
        return m;
    }
}
