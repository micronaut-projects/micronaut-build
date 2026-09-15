package io.micronaut.docs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SnippetSourceResolver {

    private static final String LANG_JAVA = "java";
    private static final String LANG_GROOVY = "groovy";
    private static final String LANG_KOTLIN = "kotlin";
    private static final String LANG_PYTHON = "python";
    private static final String LANG_SCALA = "scala";
    private static final List<String> LANGS = List.of(LANG_JAVA, LANG_PYTHON, LANG_KOTLIN, LANG_GROOVY, LANG_SCALA);
    private static final String DEFAULT_KOTLIN_PROJECT = "test-suite-kotlin";
    private static final String DEFAULT_PYTHON_PROJECT = "test-suite-python";
    private static final String DEFAULT_JAVA_PROJECT = "test-suite";
    private static final String DEFAULT_GROOVY_PROJECT = "test-suite-groovy";
    private static final String DEFAULT_SCALA_PROJECT = "test-suite-scala";
    private static final String ATTR_PROJECT = "project";
    private static final String ATTR_SOURCE = "source";
    private static final String ATTR_PROJECT_BASE = "project-base";
    private static final String ATTR_LANGUAGE = "language";
    private static final String ATTR_LANGUAGES = "languages";
    private static final Pattern SNIPPET_MACRO = Pattern.compile("(?m)^\\s*snippet::([^\\[]+)\\[([^\\]]*)]");

    private SnippetSourceResolver() {
    }

    public static List<String> languagesToRender(String defaultLanguage) {
        String language = normalize(defaultLanguage);
        if (language != null && LANGS.contains(language)) {
            return List.of(language);
        }
        return LANGS;
    }

    /**
     * @return The supported snippet languages in their rendering order
     */
    public static List<String> getLanguages() {
        return LANGS;
    }

    /**
     * Resolve the language set for a snippet macro or source-tracking pass.
     * An explicit {@code language} attribute takes precedence over
     * {@code languages}, which takes precedence over the guide language.
     *
     * @param defaultLanguage The language of a language-specific guide
     * @param attributes      The snippet attributes
     * @return The languages to render
     */
    public static List<String> languagesToRender(String defaultLanguage, Map<String, Object> attributes) {
        return languagesToRender(
            defaultLanguage,
            valueAtAttributes(ATTR_LANGUAGE, attributes),
            valueAtAttributes(ATTR_LANGUAGES, attributes)
        );
    }

    /**
     * Resolve the language set for a snippet macro or source-tracking pass.
     *
     * @param defaultLanguage The language of a language-specific guide
     * @param language        An optional single language override
     * @param languages       An optional comma-separated language override
     * @return The languages to render
     */
    public static List<String> languagesToRender(String defaultLanguage, String language, String languages) {
        String explicitLanguage = normalize(language);
        if (explicitLanguage != null) {
            return LANGS.contains(explicitLanguage) ? List.of(explicitLanguage) : Collections.emptyList();
        }

        String explicitLanguages = normalize(languages);
        if (explicitLanguages != null) {
            Set<String> selected = new LinkedHashSet<>();
            for (String candidate : explicitLanguages.split(",")) {
                String normalized = normalize(candidate);
                if (normalized != null && LANGS.contains(normalized)) {
                    selected.add(normalized);
                }
            }
            return List.copyOf(selected);
        }

        return languagesToRender(defaultLanguage);
    }

    public static Set<File> findSnippetSourceFiles(File sourceDir, File baseDir, String defaultLanguage) {
        if (!sourceDir.isDirectory()) {
            return Collections.emptySet();
        }
        Set<File> result = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(sourceDir.toPath())) {
            paths.filter(Files::isRegularFile)
                .filter(SnippetSourceResolver::isAsciiDoc)
                .forEach(path -> result.addAll(findSnippetSourceFiles(path, baseDir, defaultLanguage)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    public static Set<File> findSnippetSourceFiles(Path asciiDoc, File baseDir, String defaultLanguage) {
        String content;
        try {
            content = Files.readString(asciiDoc, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Set<File> result = new LinkedHashSet<>();
        Matcher matcher = SNIPPET_MACRO.matcher(content);
        while (matcher.find()) {
            String target = matcher.group(1);
            Map<String, Object> attributes = parseAttributes(matcher.group(2));
            for (String language : languagesToRender(defaultLanguage, attributes)) {
                for (String fileName : splitTargets(target)) {
                    File snippetFile = resolveSnippetFile(baseDir, language, fileName, attributes);
                    if (snippetFile.exists()) {
                        result.add(snippetFile);
                    }
                }
            }
        }
        return result;
    }

    public static File resolveSnippetFile(File baseDir, String language, String fileName, Map<String, Object> attributes) {
        String baseName = fileName.trim().replace(".", File.separator);
        if (LANG_PYTHON.equals(language) && baseName.startsWith("io" + File.separator)) {
            baseName = baseName.substring(3);
        }
        return new File(baseDir, projectDir(language, attributes)
            + File.separator + "src"
            + File.separator + sourceType(attributes)
            + File.separator + language
            + File.separator + baseName + "." + extension(language));
    }

    private static List<String> splitTargets(String target) {
        String[] targets = target.split(",");
        List<String> result = new ArrayList<>(targets.length);
        for (String current : targets) {
            String trimmed = current.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static Map<String, Object> parseAttributes(String attributes) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String attribute : splitAttributes(attributes)) {
            int equals = attribute.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = attribute.substring(0, equals).trim();
            String value = attribute.substring(equals + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, unquote(value));
            }
        }
        return result;
    }

    private static List<String> splitAttributes(String attributes) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < attributes.length(); i++) {
            char c = attributes.charAt(i);
            if ((c == '\'' || c == '"') && quote == 0) {
                quote = c;
            } else if (c == quote) {
                quote = 0;
            }
            if (c == ',' && quote == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String projectDir(String language, Map<String, Object> attributes) {
        String projectBase = valueAtAttributes(ATTR_PROJECT_BASE, attributes);
        if (projectBase != null && !projectBase.isEmpty()) {
            return projectBase + "-" + language;
        }

        String project = valueAtAttributes(ATTR_PROJECT, attributes);
        if (project != null && !project.isEmpty()) {
            return project;
        }
        if (LANG_KOTLIN.equals(language)) {
            return DEFAULT_KOTLIN_PROJECT;
        }
        if (LANG_PYTHON.equals(language)) {
            return DEFAULT_PYTHON_PROJECT;
        }
        if (LANG_GROOVY.equals(language)) {
            return DEFAULT_GROOVY_PROJECT;
        }
        if (LANG_SCALA.equals(language)) {
            return DEFAULT_SCALA_PROJECT;
        }
        return DEFAULT_JAVA_PROJECT;
    }

    private static String sourceType(Map<String, Object> attributes) {
        String sourceType = valueAtAttributes(ATTR_SOURCE, attributes);
        return sourceType == null || sourceType.isEmpty() ? "test" : sourceType;
    }

    private static String extension(String language) {
        if (LANG_KOTLIN.equals(language)) {
            return "kt";
        }
        if (LANG_PYTHON.equals(language)) {
            return "py";
        }
        return language;
    }

    private static String valueAtAttributes(String name, Map<String, Object> attributes) {
        Object value = attributes.get(name);
        return value == null ? null : value.toString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isAsciiDoc(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".adoc") || fileName.endsWith(".asciidoc") || fileName.endsWith(".ad");
    }
}
