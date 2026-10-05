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

import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The lines changed in the working tree of a directory since the merge base of a ref and {@code HEAD}, untracked
 * files included. It is an input of the {@code sonarLint} task, so that the task reruns when the changes do.
 * The result is a line {@code mergeBase <commit>}, followed by a line per changed file:
 * {@code <path relative to the directory>\t<first>-<last>,<line>,...}.
 */
public abstract class GitChangedLines implements ValueSource<String, GitChangedLines.Params> {

    private static final String MERGE_BASE = "mergeBase ";
    private static final Pattern HUNK = Pattern.compile("^@@ -\\S+ \\+(\\d+)(?:,(\\d+))? @@");

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public String obtain() {
        String ref = getParameters().getRef().get();
        File dir = getParameters().getDirectory().get().getAsFile();
        String mergeBase = git(dir, ref, "merge-base", ref, "HEAD").trim();
        Map<String, Set<Integer>> changed = new TreeMap<>();
        String current = null;
        String diff = git(dir, ref, "-c", "core.quotepath=false", "diff", "-U0", "--no-color", "--no-ext-diff",
            "--no-renames", "--relative", "--diff-filter=AM", mergeBase, "--", ".");
        for (String line : diff.split("\n")) {
            if (line.startsWith("+++ ")) {
                current = line.startsWith("+++ b/") ? line.substring(6) : null;
            } else if (current != null) {
                Matcher m = HUNK.matcher(line);
                if (m.find()) {
                    int start = Integer.parseInt(m.group(1));
                    int count = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
                    Set<Integer> lines = changed.computeIfAbsent(current, k -> new TreeSet<>());
                    for (int i = 0; i < count; i++) {
                        lines.add(start + i);
                    }
                }
            }
        }
        String untracked = git(dir, ref, "-c", "core.quotepath=false", "ls-files", "--others", "--exclude-standard", "--", ".");
        for (String f : untracked.split("\n")) {
            if (!f.isBlank()) {
                try (Stream<String> lines = Files.lines(dir.toPath().resolve(f), StandardCharsets.UTF_8)) {
                    int count = (int) lines.count();
                    Set<Integer> all = changed.computeIfAbsent(f, k -> new TreeSet<>());
                    for (int i = 1; i <= count; i++) {
                        all.add(i);
                    }
                } catch (IOException | UncheckedIOException e) {
                    // not a text file, so not a source file
                }
            }
        }
        StringBuilder result = new StringBuilder(MERGE_BASE).append(mergeBase).append('\n');
        changed.forEach((file, lines) -> {
            if (!lines.isEmpty()) {
                result.append(file).append('\t').append(ranges(lines)).append('\n');
            }
        });
        return result.toString();
    }

    /**
     * Parses the result of {@link #obtain()}.
     *
     * @param changes the changes
     * @param directory the directory the paths are relative to
     * @return the changed lines per absolute normalized file
     */
    static Map<Path, Set<Integer>> parse(String changes, Path directory) {
        Map<Path, Set<Integer>> result = new TreeMap<>();
        for (String line : changes.split("\n")) {
            int tab = line.lastIndexOf('\t');
            if (line.startsWith(MERGE_BASE) || tab < 0) {
                continue;
            }
            Set<Integer> lines = new TreeSet<>();
            for (String range : line.substring(tab + 1).split(",")) {
                int dash = range.indexOf('-');
                int first = Integer.parseInt(dash < 0 ? range : range.substring(0, dash));
                int last = dash < 0 ? first : Integer.parseInt(range.substring(dash + 1));
                for (int i = first; i <= last; i++) {
                    lines.add(i);
                }
            }
            result.put(directory.resolve(line.substring(0, tab)).toAbsolutePath().normalize(), lines);
        }
        return result;
    }

    static String mergeBase(String changes) {
        return changes.substring(MERGE_BASE.length(), changes.indexOf('\n'));
    }

    private static String ranges(Set<Integer> lines) {
        StringBuilder sb = new StringBuilder();
        int start = -1;
        int previous = -1;
        for (int line : lines) {
            if (start < 0) {
                start = line;
            } else if (line != previous + 1) {
                append(sb, start, previous);
                start = line;
            }
            previous = line;
        }
        append(sb, start, previous);
        return sb.toString();
    }

    private static void append(StringBuilder sb, int first, int last) {
        if (!sb.isEmpty()) {
            sb.append(',');
        }
        sb.append(first);
        if (last != first) {
            sb.append('-').append(last);
        }
    }

    private String git(File dir, String ref, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ExecResult result = getExecOperations().exec(spec -> {
            spec.setWorkingDir(dir);
            spec.executable("git");
            spec.args((Object[]) args);
            spec.setStandardOutput(out);
            spec.setErrorOutput(err);
            spec.setIgnoreExitValue(true);
        });
        if (result.getExitValue() != 0) {
            String error = err.toString(StandardCharsets.UTF_8).trim();
            if ("merge-base".equals(args[0])) {
                throw new GradleException("sonarLint: cannot find the merge base of '" + ref + "' and HEAD: " + error
                    + ". Fetch the base branch, set it with -PsonarLint.baseRef=<ref> (or micronautBuild.sonarLint.baseRef),"
                    + " or analyse every file with -PsonarLint.all=true.");
            }
            throw new GradleException("sonarLint: git " + String.join(" ", args) + " failed: " + error);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * The parameters.
     */
    public interface Params extends ValueSourceParameters {
        DirectoryProperty getDirectory();

        Property<String> getRef();
    }
}
