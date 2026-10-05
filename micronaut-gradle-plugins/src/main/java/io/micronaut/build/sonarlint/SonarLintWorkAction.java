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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.GradleException;
import org.gradle.workers.WorkAction;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.analysis.AnalysisScheduler;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;
import org.sonarsource.sonarlint.core.commons.tracing.Trace;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Runs the SonarLint analysis engine in an isolated class loader and writes the raw issues as JSON.
 * The type and severity of each issue are the ones of the quality profile.
 */
public abstract class SonarLintWorkAction implements WorkAction<SonarLintWorkParameters> {

    static final String BUNDLED_RULES = "/io/micronaut/build/sonarlint/micronaut-profile-rules.json";

    private static final Pattern RULE_METADATA = Pattern.compile("org/sonar/l10n/java/rules/java(?:se)?/(S\\d+)\\.json");
    private static final String MODULE = "sonarlint";

    @Override
    public void execute() {
        try {
            analyse();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new GradleException("SonarLint analysis failed: " + e.getMessage(), e);
        }
    }

    private void analyse() throws Exception {
        SonarLintWorkParameters params = getParameters();
        Path baseDir = params.getBaseDir().get().getAsFile().toPath();
        Set<Path> analyzers = new LinkedHashSet<>();
        params.getAnalyzers().getFiles().stream().map(File::toPath).sorted().forEach(analyzers::add);

        Map<String, Rule> rules = loadRules();
        Set<String> known = knownRules(analyzers);
        Set<String> skipped = params.getSkippedRules().get();
        List<ActiveRule> activeRules = new ArrayList<>();
        for (Rule r : rules.values()) {
            if (!skipped.contains(r.key()) && known.contains(r.key())) {
                activeRules.add(new SimpleActiveRule(RuleKey.parse(r.key()), r.severity(), r.params()));
            }
        }

        List<ClientInputFile> inputs = new ArrayList<>();
        params.getMainFiles().get().forEach(f -> inputs.add(new FileInput(Path.of(f), baseDir, false)));
        params.getTestFiles().get().forEach(f -> inputs.add(new FileInput(Path.of(f), baseDir, true)));

        LogOutput log = new LogOutput() {
            @Override
            public void log(String message, Level level, String stacktrace) {
                if (level == Level.ERROR) {
                    System.err.println("[sonarlint engine] " + message);
                }
            }
        };
        SonarLintLogger.get().setTarget(log);
        SonarLintLogger.get().setLevel(LogOutput.Level.WARN);
        PluginsLoadResult loaded = new PluginsLoader().load(
            new PluginsLoader.Configuration(analyzers, Set.of(SonarLanguage.JAVA), false, Optional.empty()), Set.of());
        Path workDir = params.getWorkDir().get().getAsFile().toPath();
        Files.createDirectories(workDir);
        ClientModuleFileSystem fileSystem = new ClientModuleFileSystem() {
            @Override
            public java.util.stream.Stream<ClientInputFile> files(String suffix, InputFile.Type type) {
                return inputs.stream().filter(i -> i.relativePath().endsWith(suffix) && i.isTest() == (type == InputFile.Type.TEST));
            }

            @Override
            public java.util.stream.Stream<ClientInputFile> files() {
                return inputs.stream();
            }
        };
        AnalysisScheduler scheduler = new AnalysisScheduler(
            AnalysisSchedulerConfiguration.builder()
                .setWorkDir(workDir)
                .setClientPid(ProcessHandle.current().pid())
                .setFileSystemProvider(module -> fileSystem)
                .build(),
            loaded.getLoadedPlugins(), log);
        List<Map<String, Object>> found = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        try {
            Map<String, String> props = new LinkedHashMap<>(params.getAnalysisProperties().get());
            AnalysisConfiguration config = AnalysisConfiguration.builder()
                .setBaseDir(baseDir)
                .addInputFiles(inputs)
                .addActiveRules(activeRules)
                .putAllExtraProperties(props)
                .build();
            List<Issue> issues = new CopyOnWriteArrayList<>();
            Set<URI> uris = new LinkedHashSet<>();
            inputs.forEach(i -> uris.add(i.uri()));
            AnalyzeCommand cmd = new AnalyzeCommand(MODULE, UUID.randomUUID(), TriggerType.FORCED, () -> config,
                issues::add, Trace.begin(MODULE, "analysis"), new SonarLintCancelMonitor(), new TaskManager(),
                f -> { }, () -> true, uris, props);
            scheduler.post(cmd);
            AnalysisResults results = cmd.getFutureResult().get(1, TimeUnit.HOURS);
            for (ClientInputFile failed : results.failedAnalysisFiles()) {
                failedFiles.add(Path.of(failed.uri()).toString());
            }
            for (Issue issue : issues) {
                if (issue.getInputFile() == null) {
                    continue;
                }
                String key = issue.getRuleKey().toString();
                Rule rule = rules.get(key);
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("file", Path.of(issue.getInputFile().uri()).toString());
                var range = issue.getTextRange();
                o.put("line", range == null ? 0 : range.getStartLine());
                o.put("column", range == null ? 0 : range.getStartLineOffset() + 1);
                o.put("endLine", range == null ? 0 : range.getEndLine());
                o.put("endColumn", range == null ? 0 : range.getEndLineOffset() + 1);
                o.put("rule", key);
                o.put("type", rule != null ? rule.type() : "CODE_SMELL");
                o.put("severity", rule != null ? rule.severity() : issue.getActiveRule().severity());
                o.put("message", issue.getMessage());
                found.add(o);
            }
        } finally {
            scheduler.stop();
            loaded.getLoadedPlugins().close();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeRules", activeRules.size());
        out.put("unknownRules", rules.keySet().stream().filter(k -> !known.contains(k)).sorted().toList());
        out.put("failedFiles", failedFiles);
        out.put("issues", found);
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        Files.writeString(params.getIssuesFile().get().getAsFile().toPath(), gson.toJson(out), StandardCharsets.UTF_8);
    }

    private Map<String, Rule> loadRules() throws IOException {
        JsonObject root;
        if (getParameters().getRulesFile().isPresent()) {
            try (Reader r = Files.newBufferedReader(getParameters().getRulesFile().get().getAsFile().toPath(), StandardCharsets.UTF_8)) {
                root = new Gson().fromJson(r, JsonObject.class);
            }
        } else {
            try (InputStream in = SonarLintWorkAction.class.getResourceAsStream(BUNDLED_RULES)) {
                if (in == null) {
                    throw new IllegalStateException("The bundled rules " + BUNDLED_RULES + " are missing");
                }
                root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            }
        }
        Map<String, Rule> rules = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("rules")) {
            JsonObject o = e.getAsJsonObject();
            Map<String, String> ruleParams = new LinkedHashMap<>();
            JsonElement paramsElement = o.get("params");
            if (paramsElement != null && paramsElement.isJsonObject()) {
                paramsElement.getAsJsonObject().entrySet().forEach(p -> ruleParams.put(p.getKey(), p.getValue().getAsString()));
            }
            String key = o.get("key").getAsString();
            rules.put(key, new Rule(key, o.get("severity").getAsString(), o.get("type").getAsString(), ruleParams));
        }
        return rules;
    }

    /**
     * The rule keys the analyzers define: the rule metadata files in their jars. The profile can hold rules of
     * commercial analyzers that are not available locally.
     */
    private static Set<String> knownRules(Set<Path> analyzers) throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path jar : analyzers) {
            try (ZipFile z = new ZipFile(jar.toFile())) {
                z.stream().forEach(en -> {
                    Matcher m = RULE_METADATA.matcher(en.getName());
                    if (m.matches()) {
                        keys.add("java:" + m.group(1));
                    }
                });
            }
        }
        return keys;
    }

    private record Rule(String key, String severity, String type, Map<String, String> params) {
    }

    private record SimpleActiveRule(RuleKey ruleKey, String severity, Map<String, String> params) implements ActiveRule {
        @Override
        public String language() {
            return "java";
        }

        @Override
        public String param(String key) {
            return params.get(key);
        }

        @Override
        public String internalKey() {
            return null;
        }

        @Override
        public String templateRuleKey() {
            return null;
        }

        @Override
        public String qpKey() {
            return MODULE;
        }
    }

    private record FileInput(Path path, Path baseDir, boolean isTest) implements ClientInputFile {
        @Override
        public String getPath() {
            return path.toString();
        }

        @Override
        public String relativePath() {
            return baseDir.relativize(path).toString().replace(File.separatorChar, '/');
        }

        @Override
        public Charset getCharset() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public SonarLanguage language() {
            return SonarLanguage.JAVA;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <G> G getClientObject() {
            return (G) this;
        }

        @Override
        public InputStream inputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public String contents() throws IOException {
            return Files.readString(path);
        }

        @Override
        public URI uri() {
            return path.toUri();
        }
    }
}
