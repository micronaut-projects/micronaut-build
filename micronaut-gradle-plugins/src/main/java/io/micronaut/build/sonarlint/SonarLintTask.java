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

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyses the Java sources of a project with SonarSource's Java analyzer, offline, with the rules of a
 * SonarCloud quality profile. By default, only the files changed since the merge base of a base ref are analysed,
 * and only the issues on changed lines are reported.
 *
 * <p>Each issue is printed on its own line:</p>
 * <pre>sonarlint: &lt;file&gt;:&lt;line&gt;:&lt;column&gt;: &lt;SEVERITY&gt; &lt;TYPE&gt; &lt;rule&gt; &lt;message&gt;</pre>
 */
@CacheableTask
public abstract class SonarLintTask extends DefaultTask {

    static final String PREFIX = "sonarlint: ";

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainSources();

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestSources();

    @Classpath
    public abstract ConfigurableFileCollection getMainBinaries();

    @Classpath
    public abstract ConfigurableFileCollection getMainLibraries();

    @Classpath
    public abstract ConfigurableFileCollection getTestBinaries();

    @Classpath
    public abstract ConfigurableFileCollection getTestLibraries();

    /**
     * The analysis engine and its dependencies, loaded in an isolated class loader.
     */
    @Classpath
    public abstract ConfigurableFileCollection getEngineClasspath();

    /**
     * The analyzer plugins (sonar-java and its symbolic execution plugin).
     */
    @Classpath
    public abstract ConfigurableFileCollection getAnalyzers();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getRulesFile();

    /**
     * The checksum of the bundled rules, used when no rules file is set.
     */
    @Input
    public String getBundledRulesChecksum() {
        try (InputStream in = SonarLintTask.class.getResourceAsStream(SonarLintWorkAction.BUNDLED_RULES)) {
            if (in == null) {
                return "missing";
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Input
    public abstract MapProperty<String, String> getSkippedRules();

    @Input
    public abstract SetProperty<String> getGateTypes();

    @Input
    public abstract SetProperty<String> getGateSeverities();

    @Input
    public abstract Property<Boolean> getFailOnIssues();

    @Input
    public abstract Property<Boolean> getShowAllIssues();

    @Input
    public abstract Property<Boolean> getAllFiles();

    @Input
    public abstract ListProperty<String> getExcludes();

    @Input
    @Optional
    public abstract Property<String> getJavaSource();

    /**
     * The lines changed since the merge base of the base ref (see {@link GitChangedLines}); absent when every
     * file is analysed.
     */
    @Input
    @Optional
    public abstract Property<String> getChangedLines();

    /**
     * The directory reported paths and exclusion patterns are relative to: the root project directory.
     */
    @Internal
    public abstract DirectoryProperty getRootDirectory();

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    /**
     * The project directory relative to the root directory, which reported paths depend on.
     */
    @Input
    public String getProjectPathFromRoot() {
        return relative(getRootDirectory().get().getAsFile().toPath(), getProjectDirectory().get().getAsFile().toPath());
    }

    @OutputFile
    public abstract RegularFileProperty getJsonReport();

    @OutputFile
    public abstract RegularFileProperty getSarifReport();

    @TaskAction
    public void analyse() throws IOException {
        Path root = getRootDirectory().get().getAsFile().toPath();
        boolean all = getAllFiles().get();
        List<PathMatcher> excludes = getExcludes().get().stream()
            .map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g))
            .toList();
        Set<Path> mainFiles = javaFiles(getMainSources(), root, excludes);
        Set<Path> testFiles = javaFiles(getTestSources(), root, excludes);

        Map<Path, Set<Integer>> changedLines = null;
        if (!all) {
            changedLines = GitChangedLines.parse(getChangedLines().get(), getProjectDirectory().get().getAsFile().toPath());
            Set<Path> changed = changedLines.keySet();
            mainFiles.retainAll(changed);
            testFiles.retainAll(changed);
        }
        String scope = all ? "" : " on changed lines";
        int analysed = mainFiles.size() + testFiles.size();

        List<Map<String, Object>> issues = analysed == 0 ? List.of() : runAnalysis(root, mainFiles, testFiles);

        Set<String> gateTypes = getGateTypes().get();
        Set<String> gateSeverities = getGateSeverities().get();
        List<SonarLintIssue> reported = new ArrayList<>();
        for (Map<String, Object> raw : issues) {
            SonarLintIssue issue = SonarLintIssue.of(raw, root, gateTypes, gateSeverities);
            Path file = root.resolve(issue.file()).normalize();
            if (changedLines == null || changedLines.getOrDefault(file, Set.of()).contains(issue.line())) {
                reported.add(issue);
            }
        }
        reported.sort(Comparator.comparing(SonarLintIssue::file)
            .thenComparingInt(SonarLintIssue::line)
            .thenComparingInt(SonarLintIssue::column)
            .thenComparing(SonarLintIssue::rule));

        List<SonarLintIssue> failing = reported.stream().filter(SonarLintIssue::gateFailing).toList();
        int other = reported.size() - failing.size();
        Map<String, Long> failingByType = failing.stream()
            .collect(Collectors.groupingBy(SonarLintIssue::type, TreeMap::new, Collectors.counting()));
        String byType = failing.isEmpty() ? ""
            : failingByType.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ", " (", ")"));
        String summary = PREFIX + plural(failing.size(), "gate-failing issue") + byType
            + ", " + plural(other, "other issue") + scope + " in " + plural(analysed, "analysed file");

        Map<String, Object> summaryJson = new LinkedHashMap<>();
        summaryJson.put("analysedFiles", analysed);
        summaryJson.put("scope", all ? "all" : "changed-lines");
        summaryJson.put("mergeBase", all ? null : GitChangedLines.mergeBase(getChangedLines().get()));
        summaryJson.put(SonarLintIssue.GATE_FAILING, failing.size());
        summaryJson.put("gateFailingByType", failingByType);
        summaryJson.put("other", other);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("issues", reported.stream().map(SonarLintIssue::toMap).toList());
        json.put("summary", summaryJson);
        writeReport(getJsonReport().get().getAsFile(), JsonOutput.prettyPrint(JsonOutput.toJson(json)));
        writeReport(getSarifReport().get().getAsFile(), JsonOutput.prettyPrint(JsonOutput.toJson(sarif(reported))));

        boolean showAll = getShowAllIssues().get();
        for (SonarLintIssue issue : reported) {
            if (showAll || issue.gateFailing()) {
                getLogger().quiet(issue.format());
            }
        }
        getLogger().quiet(summary);

        if (!failing.isEmpty() && getFailOnIssues().get()) {
            StringBuilder message = new StringBuilder(PREFIX).append(plural(failing.size(), "gate-failing issue")).append(scope).append(':');
            failing.forEach(issue -> message.append('\n').append(issue.format()));
            message.append("\nReport: ").append(getJsonReport().get().getAsFile());
            throw new GradleException(message.toString());
        }
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runAnalysis(Path root, Set<Path> mainFiles, Set<Path> testFiles) {
        File issuesFile = new File(getTemporaryDir(), "issues.json");
        Map<String, String> props = new LinkedHashMap<>();
        String mainBinaries = join(getMainBinaries());
        String testBinaries = join(getTestBinaries());
        props.put("sonar.java.binaries", mainBinaries);
        props.put("sonar.java.libraries", join(getMainLibraries()));
        props.put("sonar.java.test.binaries", Stream.of(testBinaries, mainBinaries).filter(s -> !s.isEmpty()).collect(Collectors.joining(",")));
        props.put("sonar.java.test.libraries", join(getTestLibraries()));
        if (getJavaSource().isPresent()) {
            props.put("sonar.java.source", getJavaSource().get());
        }
        getWorkerExecutor().classLoaderIsolation(spec -> spec.getClasspath().from(getEngineClasspath()))
            .submit(SonarLintWorkAction.class, p -> {
                p.getBaseDir().set(root.toFile());
                p.getMainFiles().set(mainFiles.stream().map(Path::toString).toList());
                p.getTestFiles().set(testFiles.stream().map(Path::toString).toList());
                p.getAnalyzers().from(getAnalyzers());
                p.getRulesFile().set(getRulesFile());
                p.getSkippedRules().set(getSkippedRules().get().keySet());
                p.getAnalysisProperties().set(props);
                p.getWorkDir().set(new File(getTemporaryDir(), "work"));
                p.getIssuesFile().set(issuesFile);
            });
        getWorkerExecutor().await();
        Map<String, Object> result = (Map<String, Object>) new JsonSlurper().parse(issuesFile, "UTF-8");
        List<String> unknown = (List<String>) result.get("unknownRules");
        getLogger().info("sonarLint: {} rules active, {} rules of the profile unknown to the local analyzers: {}",
            result.get("activeRules"), unknown.size(), unknown);
        for (String failed : (List<String>) result.get("failedFiles")) {
            getLogger().warn("sonarLint: the analysis of {} failed", failed);
        }
        return (List<Map<String, Object>>) result.get("issues");
    }

    private static Set<Path> javaFiles(FileCollection sources, Path root, List<PathMatcher> excludes) {
        Set<Path> files = new TreeSet<>();
        for (File f : sources.getAsFileTree().matching(p -> p.include("**/*.java")).getFiles()) {
            Path path = f.toPath().toAbsolutePath().normalize();
            Path rel = root.relativize(path);
            if (excludes.stream().noneMatch(m -> m.matches(rel))) {
                files.add(path);
            }
        }
        return files;
    }

    private static Map<String, Object> sarif(List<SonarLintIssue> reported) {
        Set<String> ruleIds = new TreeSet<>();
        List<Map<String, Object>> results = new ArrayList<>();
        for (SonarLintIssue issue : reported) {
            ruleIds.add(issue.rule());
            Map<String, Object> region = new LinkedHashMap<>();
            region.put("startLine", Math.max(1, issue.line()));
            region.put("startColumn", Math.max(1, issue.column()));
            region.put("endLine", Math.max(1, issue.endLine()));
            region.put("endColumn", Math.max(1, issue.endColumn()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ruleId", issue.rule());
            result.put("level", issue.gateFailing() ? "error" : "warning");
            result.put("message", Map.of("text", issue.message()));
            result.put("locations", List.of(Map.of("physicalLocation", Map.of(
                "artifactLocation", Map.of("uri", issue.file(), "uriBaseId", "%SRCROOT%"),
                "region", region))));
            result.put("properties", Map.of("type", issue.type(), "severity", issue.severity(), SonarLintIssue.GATE_FAILING, issue.gateFailing()));
            results.add(result);
        }
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", "sonarlint");
        driver.put("informationUri", "https://github.com/SonarSource/sonar-java");
        driver.put("rules", ruleIds.stream().map(id -> Map.of("id", id)).toList());
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("tool", Map.of("driver", driver));
        run.put("results", results);
        Map<String, Object> sarif = new LinkedHashMap<>();
        sarif.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        sarif.put("version", "2.1.0");
        sarif.put("runs", List.of(run));
        return sarif;
    }

    private static void writeReport(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    private static String join(FileCollection files) {
        return files.getFiles().stream().filter(File::exists).map(File::getAbsolutePath).collect(Collectors.joining(","));
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/');
    }
}
