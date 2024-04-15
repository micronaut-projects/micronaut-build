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
package io.micronaut.build.catalogs.tasks;

import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser;
import io.micronaut.build.catalogs.internal.Library;
import io.micronaut.build.catalogs.internal.RichVersion;
import io.micronaut.build.catalogs.internal.Status;
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel;
import io.micronaut.build.catalogs.internal.VersionModel;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A task which updates version catalog files and outputs a copy
 * of them.
 */
public abstract class VersionCatalogUpdate extends DefaultTask {
    @InputDirectory
    public abstract DirectoryProperty getCatalogsDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract ListProperty<String> getRejectedQualifiers();

    @Input
    public abstract SetProperty<String> getIgnoredModules();

    @Input
    public abstract Property<Boolean> getAllowMajorUpdates();

    @Input
    public abstract MapProperty<String, String> getRejectedVersionsPerModule();

    @TaskAction
    void updateCatalogs() throws IOException, InterruptedException {
        Set<File> catalogs = getCatalogsDirectory().getAsFileTree()
            .matching(pattern -> pattern.include("*.versions.toml"))
            .getFiles();
        File outputDir = getOutputDirectory().getAsFile().get();
        File logFile = getOutputDirectory().file("updates.log").get().getAsFile();
        if (outputDir.isDirectory() || outputDir.mkdirs()) {
            if (catalogs.isEmpty()) {
                getLogger().info("Didn't find any version catalog to process");
            }
            for (File catalog : catalogs) {
                getLogger().info("Processing {}", catalog);
                updateCatalog(catalog, new File(outputDir, catalog.getName()), logFile);
            }
        } else {
            throw new GradleException("Unable to create output directory " + outputDir);
        }
    }

    private static boolean supportsUpdate(RichVersion richVersion) {
        return richVersion != null
               && richVersion.getRequire() != null
               && richVersion.getStrictly() == null
               && richVersion.getPrefer() == null
               && !richVersion.isRejectAll()
               && richVersion.getRejectedVersions() == null;
    }

    private void updateCatalog(File inputCatalog, File outputCatalog, File logFile) throws IOException, InterruptedException {
        try (PrintWriter log = newPrintWriter(logFile)) {
            log.println("Processing catalog file " + inputCatalog);
            LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
            try (FileInputStream is = new FileInputStream(inputCatalog)) {
                parser.parse(is);
            }
            List<String> lines = Files.readAllLines(inputCatalog.toPath(), StandardCharsets.UTF_8);
            boolean allowMajorUpdate = getAllowMajorUpdates().get();
            VersionCatalogTomlModel model = parser.getModel();
            DependencyHandler dependencies = getProject().getDependencies();
            ConfigurationContainer configurations = getProject().getConfigurations();
            Configuration detachedConfiguration = configurations.detachedConfiguration();
            detachedConfiguration.setCanBeConsumed(false);
            detachedConfiguration.setCanBeResolved(true);
            detachedConfiguration.setTransitive(false);
            detachedConfiguration.getResolutionStrategy()
                .cacheDynamicVersionsFor(0, TimeUnit.MINUTES);
            List<Pattern> rejectedQualifiers = getRejectedQualifiers().get()
                .stream()
                .map(qualifier -> Pattern.compile("(?i).*[.-]" + qualifier + "[.\\d-+]*"))
                .toList();
            var rejectedVersionsPerModule = getRejectedVersionsPerModule().get();
            detachedConfiguration.getResolutionStrategy().getComponentSelection().all(rules -> {
                ModuleComponentIdentifier candidateModule = rules.getCandidate();
                String candidateVersion = candidateModule.getVersion();
                var moduleIdentifier = candidateModule.getModuleIdentifier();
                rejectedQualifiers.forEach(qualifier -> {
                    if (qualifier.matcher(candidateVersion).find()) {
                        rules.reject("Rejecting qualifier " + qualifier);
                        log.println("Rejecting " + moduleIdentifier + " version " + candidateVersion + " because of qualifier '" + qualifier + "'");
                    }
                });
                var rejected = rejectedVersionsPerModule.get(moduleIdentifier.toString());
                if (rejected != null) {
                    var exclusion = Pattern.compile(rejected);
                    if (exclusion.matcher(candidateVersion).find()) {
                        rules.reject("Rejecting version " + candidateVersion + " because of configuration. It matches regular expression: " + rejected);
                        log.println("Rejecting version " + candidateVersion + " because of configuration. It matches regular expression: " + rejected);
                    }
                }
                if (!allowMajorUpdate) {
                    model.findLibrary(
                            candidateModule.getGroup(), candidateModule.getModule()
                    ).ifPresent(library -> {
                        VersionModel version = library.getVersion();
                        if (version.getReference() != null) {
                            version = model.findVersion(version.getReference()).orElse(null);
                        }
                        if (version != null) {
                            String required = version.getVersion().getRequire();
                            if (required != null) {
                                String major = majorVersionOf(required);
                                String candidateMajor = majorVersionOf(candidateVersion);
                                if (!major.equals(candidateMajor)) {
                                    rules.reject("Rejecting major version " + candidateMajor);
                                    log.println("Rejecting " + candidateModule.getModuleIdentifier() + " version " + candidateVersion + " because it's not the same major version");
                                }
                            }
                        }
                    });
                }

            });

            Set<String> ignoredModules = getIgnoredModules().get();

            model.getLibrariesTable()
                .stream()
                .filter(library -> !ignoredModules.contains(library.getModule()))
                .filter(library -> library.getVersion().getReference() != null || !requiredVersionOf(library).isEmpty())
                .map(library -> requirePom(dependencies, library))
                .forEach(dependency -> detachedConfiguration.getDependencies().add(dependency));

            ResolutionResult resolutionResult = detachedConfiguration.getIncoming()
                .getResolutionResult();
            resolutionResult
                .allComponents(result -> {
                    ModuleVersionIdentifier mid = result.getModuleVersion();
                    String latest = mid.getVersion();
                    Status targetStatus = Status.detectStatus(latest);
                    log.println("Latest release of " + mid.getModule() + " is " + latest + " (status " + targetStatus + ")");
                    model.findLibrary(mid.getGroup(), mid.getName()).ifPresent(library -> {
                        VersionModel version = library.getVersion();
                        String reference = version.getReference();
                        if (reference != null) {
                            model.findVersion(reference).ifPresent(referencedVersion -> {
                                RichVersion richVersion = referencedVersion.getVersion();
                                if (supportsUpdate(richVersion)) {
                                    String require = richVersion.getRequire();
                                    Status sourceStatus = Status.detectStatus(require);
                                    if (!Objects.equals(require, latest) && targetStatus.isAsStableOrMoreStableThan(sourceStatus)) {
                                        log.println("Updating required version from " + require + " to " + latest);
                                        String lookup = "(" + reference + "\\s*=\\s*[\"'])(.+?)([\"'])";
                                        int lineNb = referencedVersion.getPosition().line() - 1;
                                        String line = lines.get(lineNb);
                                        Matcher m = Pattern.compile(lookup).matcher(line);
                                        if (m.find()) {
                                            lines.set(lineNb, m.replaceAll("$1" + latest + "$3"));
                                        } else {
                                            log.println("Line " + lineNb + " contains unsupported notation, automatic updating failed");
                                        }
                                    }
                                } else {
                                    log.println("Version '" + reference + "' uses a notation which is not supported for automatic upgrades yet.");
                                }
                            });
                        } else {
                            String lookup = "(version\\s*=\\s*[\"'])(.+?)([\"'])";
                            int lineNb = library.getPosition().line() - 1;
                            String line = lines.get(lineNb);
                            Matcher m = Pattern.compile(lookup).matcher(line);
                            if (m.find()) {
                                lines.set(lineNb, m.replaceAll("$1" + latest + "$3"));
                            } else {
                                lookup = "(\\s*=\\s*[\"'])(" + library.getGroup() + "):(" + library.getName() + "):(.+?)([\"'])";
                                m = Pattern.compile(lookup).matcher(line);
                                if (m.find()) {
                                    lines.set(lineNb, m.replaceAll("$1$2:$3:" + latest + "$5"));
                                } else {
                                    log.println("Line " + lineNb + " contains unsupported notation, automatic updating failed");
                                }
                            }
                        }
                    });
                });

            getLogger().lifecycle("Writing updated catalog at " + outputCatalog);
            try (PrintWriter writer = newPrintWriter(outputCatalog)) {
                lines.forEach(writer::println);
            }

            String errors = resolutionResult.getAllDependencies()
                .stream()
                .filter(UnresolvedDependencyResult.class::isInstance)
                .map(UnresolvedDependencyResult.class::cast)
                .map(r -> {
                    log.println("Unresolved dependency " + r.getAttempted().getDisplayName());
                    log.println("   reason " + r.getAttemptedReason());
                    log.println("   failure");
                    r.getFailure().printStackTrace(log);
                    return "\n    - " + r.getAttempted().getDisplayName() + " -> " + r.getFailure().getMessage();
                })
                .collect(Collectors.joining(""));
            if (!errors.isEmpty()) {
                throw new GradleException("Some modules couldn't be updated because of the following reasons:" + errors);
            }
        }
    }

    private static String requiredVersionOf(Library library) {
        RichVersion version = library.getVersion().getVersion();
        if (version != null) {
            String require = version.getRequire();
            if (require != null) {
                return require;
            }
        }
        return "";
    }

    private static Dependency requirePom(DependencyHandler dependencies, Library library) {
        ExternalModuleDependency dependency = (ExternalModuleDependency) dependencies.create(library.getGroup() + ":" + library.getName() + ":+");
        dependency.artifact(artifact -> artifact.setType("pom"));
        return dependency;
    }

    private static PrintWriter newPrintWriter(File file) throws FileNotFoundException {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
    }

    private static String majorVersionOf(String version) {
        int idx = version.indexOf(".");
        if (idx < 0) {
            return version;
        }
        return version.substring(0, idx);
    }
}
