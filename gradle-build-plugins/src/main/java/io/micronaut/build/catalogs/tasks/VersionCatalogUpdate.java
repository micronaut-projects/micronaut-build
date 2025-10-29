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
import io.micronaut.build.compat.MavenMetadataVersionHelper;
import io.micronaut.build.utils.ComparableVersion;
import io.micronaut.build.utils.Downloader;
import io.micronaut.build.utils.VersionParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;

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
    public abstract Property<Boolean> getAllowMinorUpdates();

    @Input
    public abstract MapProperty<String, String> getRejectedVersionsPerModule();

    @Internal
    public abstract Property<Boolean> getFailWhenNoVersionFound();

    @Input
    public abstract ListProperty<URI> getRepositoryBaseUris();

    public VersionCatalogUpdate() {
        getRepositoryBaseUris().convention(
            getProject().getRepositories().stream()
                .filter(MavenArtifactRepository.class::isInstance)
                .map(MavenArtifactRepository.class::cast)
                .map(MavenArtifactRepository::getUrl)
                .toList()
        );
    }

    protected void processCandidate(CandidateDetails details) {

    }

    @TaskAction
    void updateCatalogs() throws IOException, InterruptedException {
        Set<File> catalogs = getCatalogsDirectory().getAsFileTree()
            .matching(pattern -> pattern.include("*.versions.toml"))
            .getFiles();
        File outputDir = getOutputDirectory().getAsFile().get();
        if (outputDir.isDirectory() || outputDir.mkdirs()) {
            if (catalogs.isEmpty()) {
                getLogger().info("Didn't find any version catalog to process");
            }
            for (File catalog : catalogs) {
                getLogger().info("Processing {}", catalog);
                updateCatalog(catalog, new File(outputDir, catalog.getName()), getOutputDirectory().file(catalog.getName() + "-updates.log").get().getAsFile());
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
            boolean allowMinorUpdate = getAllowMinorUpdates().get();
            VersionCatalogTomlModel model = parser.getModel();
            DependencyHandler dependencies = getProject().getDependencies();
            ConfigurationContainer configurations = getProject().getConfigurations();
            Configuration detachedConfiguration = configurations.detachedConfiguration();
            detachedConfiguration.setCanBeConsumed(false);
            detachedConfiguration.setCanBeResolved(true);
            detachedConfiguration.setTransitive(false);
            detachedConfiguration.getResolutionStrategy()
                .cacheDynamicVersionsFor(0, TimeUnit.MINUTES);
            var rejectedQualifiers = getRejectedQualifiers().get()
                .stream()
                .map(qualifier -> Pattern.compile("(?i)" + qualifier + "[.\\d-+]*"))
                .toList();
            var rejectedVersionsPerModule = getRejectedVersionsPerModule().get();
            var ignoredModules = getIgnoredModules().get();

            var allDetails = model.getLibrariesTable()
                .stream()
                .filter(library -> !ignoredModules.contains(library.getModule()))
                .filter(library -> library.getVersion().getReference() != null || !requiredVersionOf(library).isEmpty())
                .parallel()
                .map(library -> findBestVersion(model, log, library, rejectedQualifiers, rejectedVersionsPerModule, allowMajorUpdate, allowMinorUpdate))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
            List<String> unresolved = new ArrayList<>();
            for (var details : allDetails) {
                var latest = details.acceptedVersion != null ? details.acceptedVersion : details.fallbackVersion;
                if (latest!=null) {
                    var library = details.library;
                    VersionModel version = library.getVersion();
                    String reference = version.getReference();
                    if (reference != null) {
                        model.findVersion(reference).ifPresent(referencedVersion -> {
                            RichVersion richVersion = referencedVersion.getVersion();
                            if (supportsUpdate(richVersion)) {
                                String require = richVersion.getRequire();
                                if (!Objects.equals(require, latest.fullVersion())) {
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
                                log.println("[" + details.module + "] version '" + reference + "' uses a notation which is not supported for automatic upgrades yet.");
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
                } else {
                    unresolved.add("Cannot resolve module " + details.module);
                }
            }

            getLogger().lifecycle("Writing updated catalog at " + outputCatalog);
            try (PrintWriter writer = newPrintWriter(outputCatalog)) {
                lines.forEach(writer::println);
            }

            if (!unresolved.isEmpty()) {
                var errors = unresolved.stream().map(s -> "    - " + s).collect(Collectors.joining("\n"));
                boolean fail = getFailWhenNoVersionFound().getOrElse(Boolean.TRUE);
                if (fail) {
                    throw new GradleException("Some modules couldn't be updated because of the following reasons:" + errors);
                } else {
                    getLogger().lifecycle("Some modules had all versions rejected or no candidate, check the logs for details");
                    log.println(errors);
                }
            }
        }
    }

    private Optional<DefaultCandidateDetails> findBestVersion(VersionCatalogTomlModel model,
                                                              PrintWriter log,
                                                              Library library,
                                                              List<Pattern> rejectedQualifiers,
                                                              Map<String, String> rejectedVersionsPerModule,
                                                              boolean allowMajorUpdates,
                                                              boolean allowMinorUpdates) {
        var reference = library.getVersion().getReference();
        String version;
        if (reference != null) {
            version = model.findVersion(reference).map(VersionModel::getVersion).map(RichVersion::getRequire).orElse(null);
        } else {
            version = library.getVersion().getVersion().getRequire();
        }
        if (version != null) {
            var group = library.getGroup();
            var name = library.getName();
            var currentVersion = VersionParser.parse(version);
            var module = group + ":" + name;
            var candidateDetails = new DefaultCandidateDetails(log, library, currentVersion);
            var comparableVersions = fetchVersions(group, name);
            for (var candidateVersion : comparableVersions) {
                candidateDetails.prepare(candidateVersion);
                var candidateStatus = Status.detectStatus(candidateVersion.fullVersion());
                var sourceStatus = Status.detectStatus(currentVersion.fullVersion());
                if (!candidateStatus.isAsStableOrMoreStableThan(sourceStatus)) {
                    candidateDetails.rejectCandidate("it's not as stable as " + sourceStatus);
                }
                if (candidateVersion.qualifier().isPresent()) {
                    var candidateQualifier = candidateVersion.qualifier().get();
                    rejectedQualifiers.forEach(qualifier -> {
                        if (qualifier.matcher(candidateQualifier).find()) {
                            candidateDetails.rejectCandidate("of qualifier '" + qualifier + "'");
                        }
                    });
                }
                var rejected = rejectedVersionsPerModule.get(module);
                if (rejected != null) {
                    var exclusion = Pattern.compile(rejected);
                    if (exclusion.matcher(candidateVersion.fullVersion()).find()) {
                        candidateDetails.rejectCandidate("of configuration. It matches regular expression: " + rejected);
                    }
                }
                maybeRejectVersionByMinorMajor(allowMajorUpdates, allowMinorUpdates, currentVersion, candidateVersion, candidateDetails);
                if (!candidateDetails.isRejected()) {
                    processCandidate(candidateDetails);
                }
                if (candidateDetails.isAccepted()) {
                    break;
                }
                if (!candidateDetails.isRejected() && !candidateDetails.hasFallback()) {
                    candidateDetails.acceptCandidate();
                    break;
                }
            }
            return Optional.of(candidateDetails);
        }
        return Optional.empty();
    }

    public List<ComparableVersion> fetchVersions(String groupId, String artifactId) {
        var uris = getRepositoryBaseUris().get();
        for (var baseUrl : uris) {
            var metadata = URI.create(baseUrl.toString() + "/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml");
            var data = Downloader.doDownload(metadata);
            if (data != null) {
                var list = MavenMetadataVersionHelper.findReleasesFrom(data)
                    .stream()
                    .sorted(reverseOrder())
                    .toList();
                if (!list.isEmpty()) {
                    // Goto next repository
                    return list;
                }
            }
        }
        return List.of();
    }

    // Visible for testing
    static void maybeRejectVersionByMinorMajor(boolean allowMajorUpdate,
                                               boolean allowMinorUpdate,
                                               ComparableVersion currentVersion,
                                               ComparableVersion candidateVersion,
                                               CandidateDetails details) {
        if (!allowMajorUpdate || !allowMinorUpdate) {
            int major = currentVersion.major().orElse(0);
            int candidateMajor = candidateVersion.major().orElse(0);
            if (major != candidateMajor && !allowMajorUpdate) {
                details.rejectCandidate("it's not the same major version as current : " + currentVersion + " (current) vs " + candidateVersion + " (candidate)");
            } else if (major == candidateMajor && !allowMinorUpdate) {
                int minor = currentVersion.minor().orElse(0);
                int candidateMinor = candidateVersion.minor().orElse(0);
                if (minor != candidateMinor) {
                    details.rejectCandidate("it's not the same minor version : "  + currentVersion + " (current) vs " + candidateVersion + " (candidate)");
                }
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

    private static PrintWriter newPrintWriter(File file) throws FileNotFoundException {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
    }

    /**
     * Stateful details about a candidate. Allows subclasses to
     * perform custom selection.
     */
    protected interface CandidateDetails {
        /**
         * The candidate module, in the group:name format
         * @return the module id
         */
        String getModule();

        /**
         * The current version of a module, as found in the catalog
         * @return the current version
         */
        ComparableVersion getCurrentVersion();

        /**
         * A candidate version of the module, as found in a repository
         * @return the candidate version
         */
        ComparableVersion getCandidateVersion();

        /**
         * Tells that this version should be selected if no better
         * match is found. The first fallback will be used, any
         * subsequent call to this method once a fallback is set
         * will be ignored.
         */
        void acceptAsFallback();

        /**
         * Rejects a candidate with a reason.
         * @param reason the reason to reject the candidate
         */
        void rejectCandidate(String reason);

        /**
         * Accepts a candidate. No other candidate will be tested.
         */
        void acceptCandidate();
    }

    private static class DefaultCandidateDetails implements CandidateDetails {
        private final Library library;
        private final PrintWriter log;
        private final String module;
        private final ComparableVersion currentVersion;
        private ComparableVersion candidateVersion;
        private ComparableVersion acceptedVersion;
        private ComparableVersion fallbackVersion;
        private boolean rejected;

        private DefaultCandidateDetails(PrintWriter log,
                                        Library library,
                                        ComparableVersion currentVersion) {
            this.log = log;
            this.library = library;
            this.module = library.getGroup() + ":" + library.getName();
            this.currentVersion = currentVersion;
        }

        @Override
        public String getModule() {
            return module;
        }

        @Override
        public ComparableVersion getCurrentVersion() {
            return currentVersion;
        }

        @Override
        public ComparableVersion getCandidateVersion() {
            return candidateVersion;
        }

        @Override
        public void acceptAsFallback() {
            if (fallbackVersion == null) {
                fallbackVersion = candidateVersion;
                String message = "[" + module + "] Accepting version '" + candidateVersion + "' as fallback in case no better match is found";
                log.println(message);
            }
            rejected = true;
        }

        @Override
        public void rejectCandidate(String reason) {
            rejected = true;
            String message = "[" + module + "] Rejecting version '" + candidateVersion + "' because " + reason;
            log.println(message);
        }

        @Override
        public void acceptCandidate() {
            acceptedVersion = candidateVersion;
            String message;
            if (currentVersion.equals(candidateVersion)) {
                message = "[" + module + "] Current version " + currentVersion + " is suitable";
            } else {
                message = "[" + module + "] Accepting candidate " + candidateVersion + " as replacement to " + currentVersion;
            }
            log.println(message);
        }

        public boolean isRejected() {
            return rejected;
        }

        public boolean hasFallback() {
            return fallbackVersion != null;
        }

        public boolean isAccepted() {
            return acceptedVersion != null;
        }

        public void prepare(ComparableVersion candidateVersion) {
            this.candidateVersion = candidateVersion;
            this.rejected = false;
        }
    }
}
