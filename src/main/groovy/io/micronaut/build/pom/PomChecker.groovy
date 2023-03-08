package io.micronaut.build.pom

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

@CompileStatic
@CacheableTask
abstract class PomChecker extends DefaultTask {
    @Input
    abstract ListProperty<String> getRepositories()

    @Input
    abstract Property<String> getPomCoordinates()

    @Input
    abstract Property<Boolean> getFailOnSnapshots()

    @Input
    abstract Property<Boolean> getFailOnError()

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    abstract RegularFileProperty getPomFile()

    @Input
    @Optional
    abstract Property<String> getProjectGroup();

    @Nested
    abstract Property<BomSuppressions> getSuppressions()

    @OutputDirectory
    abstract DirectoryProperty getReportDirectory()

    @Internal
    abstract DirectoryProperty getPomsDirectory()

    @Inject
    abstract WorkerExecutor getWorkerExecutor()

    PomChecker() {
        description = "Verifies a POM file"
        group = VERIFICATION_GROUP
        getFailOnError().convention(true)
        getFailOnSnapshots().convention(getPomCoordinates().map(v -> !v.endsWith("-SNAPSHOT")))
        getProjectGroup().convention(
                project.providers.gradleProperty("projectGroup")
                        .orElse(project.providers.gradleProperty("projectGroupId")
                                .orElse(project.provider { String.valueOf(project.group) }))
        )
    }

    @TaskAction
    void verifyBom() {
        Set<String> silencedDeps = suppressions.get().dependencies.get()
        Map<String, Set<String>> bomAuthorizedGroupIds = suppressions.get().bomAuthorizedGroupIds.get()
        ErrorCollector errorCollector = new ErrorCollector(silencedDeps)
        def coordinates = pomCoordinates.get().split(':')
        if (coordinates.length != 3) {
            throw new GradleException("Incorrect POM coordinates '${pomCoordinates.get()}': should be of the form group:artifact:version ")
        }
        def queue = new ArrayDeque<List<Object>>()
        queue.add([coordinates[0], coordinates[1], coordinates[2], pomFile.get().asFile, pomCoordinates.get()] as List<Object>)
        def workQueue = workerExecutor.noIsolation()
        Set<String> seen = []
        while (!queue.isEmpty()) {
            List<File> reports = []
            queue.each { item ->
                def (group, artifact, version, pomFile, path) = [item[0], item[1], item[2], item[3], item[4]]
                String key = "${group}:${artifact}:${version}"
                if (seen.add(key)) {
                    workQueue.submit(CheckPomAction) { CheckPomAction.Parameters params ->
                        params.pomFile.set((File) pomFile)
                        params.groupId.set((String) group)
                        params.artifactId.set((String) artifact)
                        params.version.set((String) version)
                        params.repositories.set(repositories)
                        params.getDependencyPath().set((String) path)
                        def reportFile = reportDirectory.file("${group}-${artifact}-${version}.json")
                        reports.add(reportFile.get().asFile)
                        params.reportFile.set(reportFile)
                        params.pomDirectory.set(pomsDirectory)
                    }
                }
            }
            workQueue.await()
            queue.clear()
            reports.each {
                String projectGroupId = projectGroup.getOrElse("io.micronaut")
                def validation = PomFileAdapter.parseFromFile(it)
                String bomPrefix = "POM ${validation.pomFile.groupId}:${validation.pomFile.artifactId}:${validation.pomFile.version} (via ${validation.dependencyPath})"
                assertThatImportingBomIsAllowed(validation, errorCollector)
                if (validation.pomFile.bom) {
                    addTransitiveBomsToQueue(validation, queue)
                    if (validation.pomFile.dependencies.any { !it.managed }) {
                        errorCollector.errors.add("$bomPrefix has dependencies outside of <dependencyManagement> block.".toString())
                    }
                    def groupId = validation.pomFile.groupId
                    def artifactId = validation.pomFile.artifactId
                    def version = validation.pomFile.version
                    Set<String> allowedGroups = bomAuthorizedGroupIds.get("${groupId}:${artifactId}".toString())
                    if (allowedGroups == null) {
                        allowedGroups = bomAuthorizedGroupIds.getOrDefault("${groupId}:${artifactId}:${version}".toString(), [] as Set)
                    }
                    if (!groupId.startsWith(projectGroupId) && !isMicronautBom(groupId, artifactId)) {
                        validation.pomFile.dependencies.findAll {
                            it.managed && !it.groupId.startsWith(groupId)
                        }.each {
                            String dependency = "${it.groupId}:${it.artifactId}:${it.version}"
                            String message = "$bomPrefix declares dependency on ${dependency} which doesn't belong to group ${groupId}.".toString()
                            if (allowedGroups.contains(it.groupId)) {
                                errorCollector.silenced(message)
                            } else {
                                errorCollector.error(dependency, message)
                            }
                        }
                    }
                }
                validation.invalidDependencies.each {
                    if (!it.startsWith(projectGroupId) && !it.endsWith("-SNAPSHOT")) {
                        errorCollector.error(it, "$bomPrefix declares a non-resolvable dependency: $it".toString())
                    }
                }
                if (failOnSnapshots.get()) {
                    validation.pomFile.dependencies.findAll {
                        !it.groupId.equals(projectGroupId) && it.version.endsWith("-SNAPSHOT")
                    }.each {
                        String dependency = "${it.groupId}:${it.artifactId}:${it.version}"
                        errorCollector.error(dependency, "$bomPrefix declares a SNAPSHOT dependency on ${dependency}".toString())
                    }
                }
            }
        }

        File reportFile = writeReport(errorCollector.errors)
        if (failOnError.get() && errorCollector.errors) {
            throw new GradleException("POM verification failed. See report in ${reportFile}")
        }
    }

    /**
     * Determines if the GAV coordinates correspond to a Micronaut BOM.
     * @param groupId the group ID
     * @param artifactId the artifact id
     * @return true if the GAV coordinates correspond to a Micronaut BOM
     */
    private static boolean isMicronautBom(String groupId, String artifactId) {
        groupId.startsWith('io.micronaut') && artifactId.contains('bom')
    }

    private File writeReport(List<String> errors) {
        def reportFile = reportDirectory.file("report-${name}.txt").get().asFile
        reportFile.withWriter { writer ->
            errors.each {
                println it
                writer.println(it)
            }
        }
        reportFile
    }

    private static void addTransitiveBomsToQueue(PomValidation validation, Deque<List<Object>> queue) {
        validation.validDependencies.each { gav, file ->
            def coord = gav.split(':')
            String group = coord[0]
            String artifact = coord[1]
            String version = coord[2]
            def dependency = validation.pomFile.dependencies.find {
                it.groupId == group && it.artifactId == artifact && it.version == version
            }
            if (dependency.managed && dependency.import) {
                queue.add([group, artifact, version, new File(file), "${validation.dependencyPath} -> $gav".toString()] as List<Object>)
            }
        }
    }

    private static void assertThatImportingBomIsAllowed(PomValidation validation, ErrorCollector errors) {
        if (validation.pomFile.bom && validation.pomFile.importingBom) {
            // We have a BOM which imports another BOM. This should only
            // be allowed for Micronaut BOMs themselves
            if (!validation.pomFile.groupId.startsWith("io.micronaut")) {
                validation.pomFile.findImports().each {
                    String dependency = "${it.groupId}:${it.artifactId}:${it.version}"
                    errors.error(dependency, "BOM ${validation.pomFile.groupId}:${validation.pomFile.artifactId}:${validation.pomFile.version} (via $validation.dependencyPath) is not a Micronaut BOM but it imports another BOM ($dependency)".toString())
                }
            }
        }
    }

    private static class ErrorCollector {
        private final Set<String> silencedDependencies
        final List<String> errors = []

        ErrorCollector(Set<String> silencedDependencies) {
            this.silencedDependencies = silencedDependencies
        }

        void silenced(String message) {
            println("[Silenced] $message")
        }

        void error(String dependency, String message) {
            if (!silencedDependencies.contains(dependency)) {
                errors << message
            } else {
                silenced(message)
            }
        }
    }
}
