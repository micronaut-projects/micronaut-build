package io.micronaut.build.pom;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP;

@CacheableTask
public abstract class PomChecker extends DefaultTask {
    @Input
    public abstract ListProperty<String> getRepositories();

    @Input
    public abstract Property<String> getPomCoordinates();

    @Input
    public abstract Property<Boolean> getFailOnSnapshots();

    @Input
    public abstract Property<Boolean> getFailOnError();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public abstract RegularFileProperty getPomFile();

    @Input
    @Optional
    public abstract Property<String> getProjectGroup();

    @Nested
    public abstract Property<BomSuppressions> getSuppressions();

    @OutputDirectory
    public abstract DirectoryProperty getReportDirectory();

    @Internal
    public abstract DirectoryProperty getPomsDirectory();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    public PomChecker() {
        setDescription("Verifies a POM file");
        setGroup(VERIFICATION_GROUP);
        getFailOnError().convention(true);
        getFailOnSnapshots().convention(getPomCoordinates().map(version -> !version.endsWith("-SNAPSHOT")));
        getProjectGroup().convention(
            getProject().getProviders().gradleProperty("projectGroup")
                .orElse(getProject().getProviders().gradleProperty("projectGroupId")
                    .orElse(getProject().provider(() -> String.valueOf(getProject().getGroup()))))
        );
    }

    @TaskAction
    public void verifyBom() {
        Set<String> silencedDeps = getSuppressions().get().getDependencies().get();
        Map<String, Set<String>> bomAuthorizedGroupIds = getSuppressions().get().getBomAuthorizedGroupIds().get();
        ErrorCollector errorCollector = new ErrorCollector(silencedDeps);
        String[] coordinates = getPomCoordinates().get().split(":");
        if (coordinates.length != 3) {
            throw new GradleException("Incorrect POM coordinates '" + getPomCoordinates().get() + "': should be of the form group:artifact:version ");
        }
        Deque<PomWorkItem> queue = new ArrayDeque<>();
        queue.add(new PomWorkItem(coordinates[0], coordinates[1], coordinates[2], getPomFile().get().getAsFile(), getPomCoordinates().get()));
        WorkQueue workQueue = getWorkerExecutor().noIsolation();
        Set<String> seen = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            List<File> reports = new ArrayList<>();
            List<PomWorkItem> batch = new ArrayList<>(queue);
            queue.clear();
            for (PomWorkItem item : batch) {
                String key = item.group() + ":" + item.artifact() + ":" + item.version();
                if (seen.add(key)) {
                    workQueue.submit(CheckPomAction.class, params -> {
                        params.getPomFile().fileValue(item.pomFile());
                        params.getGroupId().set(item.group());
                        params.getArtifactId().set(item.artifact());
                        params.getVersion().set(item.version());
                        params.getRepositories().set(getRepositories());
                        params.getDependencyPath().set(item.path());
                        Provider<RegularFile> reportFile = getReportDirectory().file(item.group() + "-" + item.artifact() + "-" + item.version() + ".json");
                        reports.add(reportFile.get().getAsFile());
                        params.getReportFile().set(reportFile);
                        params.getPomDirectory().set(getPomsDirectory());
                    });
                }
            }
            workQueue.await();
            for (File report : reports) {
                String projectGroupId = getProjectGroup().getOrElse("io.micronaut");
                PomValidation validation = PomFileAdapter.parseFromFile(report);
                String bomPrefix = "POM " + validation.getPomFile().getGroupId()
                    + ":" + validation.getPomFile().getArtifactId()
                    + ":" + validation.getPomFile().getVersion()
                    + " (via " + validation.getDependencyPath() + ")";
                assertThatImportingBomIsAllowed(validation, errorCollector);
                if (validation.getPomFile().isBom()) {
                    addTransitiveBomsToQueue(validation, queue);
                    if (validation.getPomFile().getDependencies().stream().anyMatch(dependency -> !dependency.isManaged())) {
                        errorCollector.getErrors().add(bomPrefix + " has dependencies outside of <dependencyManagement> block.");
                    }
                    String groupId = validation.getPomFile().getGroupId();
                    String artifactId = validation.getPomFile().getArtifactId();
                    String version = validation.getPomFile().getVersion();
                    Set<String> allowedGroups = bomAuthorizedGroupIds.get(groupId + ":" + artifactId);
                    if (allowedGroups == null) {
                        allowedGroups = bomAuthorizedGroupIds.getOrDefault(groupId + ":" + artifactId + ":" + version, Set.of());
                    }
                    if (!groupId.startsWith(projectGroupId) && !isMicronautBom(groupId, artifactId)) {
                        for (PomDependency dependency : validation.getPomFile().getDependencies()) {
                            if (dependency.isManaged() && !dependency.getGroupId().startsWith(groupId)) {
                                String dependencyCoordinates = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
                                String message = bomPrefix + " declares dependency on " + dependencyCoordinates + " which doesn't belong to group " + groupId + ".";
                                if (allowedGroups.contains(dependency.getGroupId())) {
                                    errorCollector.silenced(message);
                                } else {
                                    errorCollector.error(dependencyCoordinates, message);
                                }
                            }
                        }
                    }
                }
                for (String invalidDependency : validation.getInvalidDependencies()) {
                    if (!invalidDependency.startsWith(projectGroupId) && !invalidDependency.endsWith("-SNAPSHOT")) {
                        errorCollector.error(invalidDependency, bomPrefix + " declares a non-resolvable dependency: " + invalidDependency);
                    }
                }
                if (getFailOnSnapshots().get()) {
                    for (PomDependency dependency : validation.getPomFile().getDependencies()) {
                        if (!dependency.getGroupId().equals(projectGroupId) && dependency.getVersion().endsWith("-SNAPSHOT")) {
                            String dependencyCoordinates = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
                            errorCollector.error(dependencyCoordinates, bomPrefix + " declares a SNAPSHOT dependency on " + dependencyCoordinates);
                        }
                    }
                }
            }
        }

        File reportFile = writeReport(errorCollector.getErrors(), errorCollector.getSuggestions());
        if (getFailOnError().get() && !errorCollector.getErrors().isEmpty()) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8));
            try {
                writeSuggestions(errorCollector.getSuggestions(), writer);
                writer.flush();
            } catch (IOException e) {
                throw new GradleException("Unable to write POM suggestions", e);
            }
            throw new GradleException("POM verification failed. See report in " + reportFile);
        }
    }

    /**
     * Determines if the GAV coordinates correspond to a Micronaut BOM.
     * @param groupId the group ID
     * @param artifactId the artifact id
     * @return true if the GAV coordinates correspond to a Micronaut BOM
     */
    private static boolean isMicronautBom(String groupId, String artifactId) {
        return groupId.startsWith("io.micronaut") && artifactId.contains("bom");
    }

    private File writeReport(List<String> errors, Set<String> dependencySuggestions) {
        File reportFile = getReportDirectory().file("report-" + getName() + ".txt").get().getAsFile();
        try {
            Files.createDirectories(reportFile.getParentFile().toPath());
            try (BufferedWriter writer = Files.newBufferedWriter(reportFile.toPath(), StandardCharsets.UTF_8)) {
                for (String error : errors) {
                    getLogger().quiet(error);
                    writer.write(error);
                    writer.newLine();
                }
                writeSuggestions(dependencySuggestions, writer);
            }
        } catch (IOException e) {
            throw new GradleException("Unable to write POM report", e);
        }
        return reportFile;
    }

    private static void addTransitiveBomsToQueue(PomValidation validation, Deque<PomWorkItem> queue) {
        validation.getValidDependencies().forEach((gav, file) -> {
            String[] coordinates = gav.split(":");
            String group = coordinates[0];
            String artifact = coordinates[1];
            String version = coordinates[2];
            validation.getPomFile().getDependencies().stream()
                .filter(dependency -> dependency.getGroupId().equals(group)
                    && dependency.getArtifactId().equals(artifact)
                    && dependency.getVersion().equals(version))
                .findFirst()
                .filter(dependency -> dependency.isManaged() && dependency.isImport())
                .ifPresent(dependency -> queue.add(new PomWorkItem(
                    group,
                    artifact,
                    version,
                    new File(file),
                    validation.getDependencyPath() + " -> " + gav
                )));
        });
    }

    private static void assertThatImportingBomIsAllowed(PomValidation validation, ErrorCollector errors) {
        if (validation.getPomFile().isBom() && validation.getPomFile().isImportingBom()) {
            // We have a BOM which imports another BOM. This should only
            // be allowed for Micronaut BOMs themselves
            if (!validation.getPomFile().getGroupId().startsWith("io.micronaut")) {
                for (PomDependency dependency : validation.getPomFile().findImports()) {
                    String dependencyCoordinates = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
                    errors.error(
                        dependencyCoordinates,
                        "BOM " + validation.getPomFile().getGroupId()
                            + ":" + validation.getPomFile().getArtifactId()
                            + ":" + validation.getPomFile().getVersion()
                            + " (via " + validation.getDependencyPath()
                            + ") is not a Micronaut BOM but it imports another BOM (" + dependencyCoordinates + ")"
                    );
                }
            }
        }
    }

    void writeSuggestions(Set<String> dependencySuggestions, BufferedWriter writer) throws IOException {
        if (!dependencySuggestions.isEmpty()) {
            writer.newLine();
            writer.write("You can silence these problems by adding this to the BOM build script:");
            writer.newLine();
            writer.newLine();
            writer.write("micronautBom {");
            writer.newLine();
            writer.write("    suppressions {");
            writer.newLine();
            for (String dependencySuggestion : dependencySuggestions) {
                writer.write("        dependencies.add(\"" + dependencySuggestion + "\")");
                writer.newLine();
            }
            writer.write("    }");
            writer.newLine();
            writer.write("}");
            writer.newLine();
        }
    }

    private record PomWorkItem(String group, String artifact, String version, File pomFile, String path) {
    }

    private static final class ErrorCollector {
        private final Set<String> silencedDependencies;
        private final List<String> errors = new ArrayList<>();
        private final Set<String> suggestions = new LinkedHashSet<>();

        ErrorCollector(Set<String> silencedDependencies) {
            this.silencedDependencies = silencedDependencies;
        }

        List<String> getErrors() {
            return errors;
        }

        Set<String> getSuggestions() {
            return suggestions;
        }

        void silenced(String message) {
            System.out.println("[Silenced] " + message);
        }

        void error(String dependency, String message) {
            if (!silencedDependencies.contains(dependency)) {
                errors.add(message);
                suggestions.add(dependency);
            } else {
                silenced(message);
            }
        }
    }
}
