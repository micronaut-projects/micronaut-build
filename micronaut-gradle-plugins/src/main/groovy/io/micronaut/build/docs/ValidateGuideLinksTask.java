/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.build.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.micronaut.build.utils.ConsoleUtils.clickableUrl;

@CacheableTask
public abstract class ValidateGuideLinksTask extends DefaultTask {

    private static final List<String> IGNORED_SCHEMES = List.of("http", "https", "mailto", "tel", "javascript", "data");

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocsDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getHtmlFiles();

    @Input
    @Optional
    public abstract Property<Boolean> getFailOnError();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    void validate() throws IOException {
        List<String> errors = new ArrayList<>();
        File docsDirectory = getDocsDirectory().getAsFile().get().getCanonicalFile();
        for (File htmlFile : getHtmlFiles().getFiles()) {
            validateFile(docsDirectory, htmlFile.getCanonicalFile(), errors);
        }
        writeReport(errors);
        if (!errors.isEmpty() && getFailOnError().getOrElse(true)) {
            throw new GradleException(
                    "Validation of generated guide links failed. See the report at " + clickableUrl(getReport().getAsFile().get())
            );
        }
    }

    private void validateFile(File docsDirectory, File htmlFile, List<String> errors) throws IOException {
        if (!htmlFile.isFile()) {
            errors.add("Missing HTML file " + docsDirectory.toPath().relativize(htmlFile.toPath()));
            return;
        }
        Document document = Jsoup.parse(htmlFile, "UTF-8");
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href").trim();
            if (href.isEmpty() || shouldIgnore(href)) {
                continue;
            }
            LinkTarget target = parseTarget(href);
            File targetFile = target.path.isEmpty() ? htmlFile : new File(htmlFile.getParentFile(), target.path).getCanonicalFile();
            if (!isInsideDirectory(docsDirectory, targetFile)) {
                errors.add(formatError(docsDirectory, htmlFile, href, "points outside the docs directory"));
            } else if (!targetFile.isFile()) {
                errors.add(formatError(
                        docsDirectory,
                        htmlFile,
                        href,
                        "missing target file " + docsDirectory.toPath().relativize(targetFile.toPath())
                ));
            } else if (!target.fragment.isEmpty() && !containsFragment(targetFile, target.fragment)) {
                errors.add(formatError(docsDirectory, htmlFile, href, "missing fragment #" + target.fragment));
            }
        }
    }

    private static boolean shouldIgnore(String href) {
        if ("#".equals(href)) {
            return true;
        }
        if (href.startsWith("//")) {
            return true;
        }
        String lowerHref = href.toLowerCase(Locale.ROOT);
        int colon = lowerHref.indexOf(':');
        return colon > 0 && IGNORED_SCHEMES.contains(lowerHref.substring(0, colon));
    }

    private static LinkTarget parseTarget(String href) {
        String path = href;
        String fragment = "";
        int hash = path.indexOf('#');
        if (hash >= 0) {
            fragment = path.substring(hash + 1);
            path = path.substring(0, hash);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return new LinkTarget(path, fragment);
    }

    private static boolean containsFragment(File targetFile, String fragment) throws IOException {
        Document targetDocument = Jsoup.parse(targetFile, "UTF-8");
        if (targetDocument.getElementById(fragment) != null) {
            return true;
        }
        for (Element anchor : targetDocument.select("a[name]")) {
            if (fragment.equals(anchor.attr("name"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideDirectory(File directory, File file) {
        Path directoryPath = directory.toPath();
        Path filePath = file.toPath();
        return filePath.startsWith(directoryPath);
    }

    private void writeReport(List<String> errors) throws IOException {
        File report = getReport().getAsFile().get();
        report.getParentFile().mkdirs();
        try (PrintWriter prn = new PrintWriter(new FileWriter(report))) {
            if (errors.isEmpty()) {
                prn.println("No broken links found.");
            } else {
                for (String error : errors) {
                    prn.println(error);
                }
            }
        }
    }

    private static String formatError(File docsDirectory, File sourceFile, String href, String message) {
        return docsDirectory.toPath().relativize(sourceFile.toPath()) + ": link '" + href + "' " + message;
    }

    private static final class LinkTarget {
        private final String path;
        private final String fragment;

        private LinkTarget(String path, String fragment) {
            this.path = path;
            this.fragment = fragment;
        }
    }
}
