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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Exports the active rules of a SonarCloud quality profile, through the public API, to a rules file
 * the {@code sonarLint} task reads.
 */
@DisableCachingByDefault(because = "Downloads the current state of a quality profile")
public abstract class SonarLintExportRulesTask extends DefaultTask {

    public static final String DEFAULT_ORGANIZATION = "micronaut-projects";
    /**
     * The "Micronaut Profile" quality profile.
     */
    public static final String DEFAULT_QUALITY_PROFILE = "AYFHlJjqlBO-fp5MP_nD";

    private static final int PAGE_SIZE = 500;
    private static final String SEVERITY = "severity";

    @Input
    public abstract Property<String> getOrganization();

    @Input
    public abstract Property<String> getQualityProfile();

    @Input
    public abstract Property<String> getServerUrl();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    @SuppressWarnings("unchecked")
    public void export() throws IOException, InterruptedException {
        String organization = getOrganization().get();
        String profile = getQualityProfile().get();
        List<Map<String, Object>> rules = new ArrayList<>();
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            for (int page = 1; ; page++) {
                String url = getServerUrl().get() + "/api/rules/search?organization=" + enc(organization)
                    + "&qprofile=" + enc(profile) + "&activation=true&ps=" + PAGE_SIZE + "&p=" + page
                    + "&f=actives,repo,severity,lang,templateKey,internalKey,name&s=key";
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new GradleException("Cannot export the rules of " + organization + "/" + profile + ": HTTP "
                        + response.statusCode() + " " + response.body());
                }
                Map<String, Object> body = (Map<String, Object>) new JsonSlurper().parseText(response.body());
                Map<String, List<Map<String, Object>>> actives = (Map<String, List<Map<String, Object>>>) body.getOrDefault("actives", Map.of());
                for (Map<String, Object> rule : (List<Map<String, Object>>) body.get("rules")) {
                    String key = (String) rule.get("key");
                    List<Map<String, Object>> activations = actives.getOrDefault(key, List.of());
                    Map<String, Object> active = activations.stream()
                        .filter(a -> profile.equals(a.get("qProfile")))
                        .findFirst()
                        .orElse(activations.isEmpty() ? Map.of() : activations.get(0));
                    Map<String, Object> params = new TreeMap<>();
                    for (Map<String, Object> p : (List<Map<String, Object>>) active.getOrDefault("params", List.of())) {
                        params.put((String) p.get("key"), p.get("value"));
                    }
                    Map<String, Object> out = new TreeMap<>();
                    out.put("key", key);
                    out.put("name", rule.get("name"));
                    out.put(SEVERITY, active.getOrDefault(SEVERITY, rule.get(SEVERITY)));
                    out.put("type", rule.get("type"));
                    out.put("templateKey", rule.get("templateKey"));
                    out.put("internalKey", rule.get("internalKey"));
                    out.put("params", params);
                    rules.add(out);
                }
                if ((long) page * PAGE_SIZE >= ((Number) body.get("total")).longValue()) {
                    break;
                }
            }
        }
        rules.sort(Comparator.comparing(r -> (String) r.get("key")));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("organization", organization);
        json.put("qualityProfile", profile);
        json.put("rules", rules);
        var file = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n", StandardCharsets.UTF_8);
        getLogger().lifecycle("sonarLint: {} rules of {}/{} written to {}", rules.size(), organization, profile, file);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
