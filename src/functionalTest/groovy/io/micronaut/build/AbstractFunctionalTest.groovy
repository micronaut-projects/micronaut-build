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
package io.micronaut.build

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

abstract class AbstractFunctionalTest extends Specification {

    @TempDir
    Path testDirectory

    String gradleVersion
    boolean debug

    private StringWriter outputWriter
    private StringWriter errorOutputWriter
    private String output
    private String errorOutput

    BuildResult result

    File file(String child) {
        def fileRef = testDirectory.resolve(child).toFile()
        fileRef.parentFile.mkdirs()
        fileRef
    }

    File getGroovyBuildFile() {
        file("build.gradle")
    }

    File getKotlinBuildFile() {
        file("build.gradle.kts")
    }

    File getGroovySettingsFile() {
        file("settings.gradle")
    }

    File getKotlinSettingsFile() {
        file("settings.gradle.kts")
    }

    File getBuildFile() {
        groovyBuildFile
    }

    File getSettingsFile() {
        groovySettingsFile
    }

    void run(String... args) {
        try {
            result = newRunner(args)
                    .build()
        } finally {
            recordOutputs()
        }
    }

    void outputContains(String text) {
        assert output.contains(text)
    }

    void outputDoesNotContain(String text) {
        assert !output.contains(text)
    }

    void errorOutputContains(String text) {
        assert errorOutput.contains(text)
    }

    void tasks(@DelegatesTo(value = TaskExecutionGraph, strategy = Closure.DELEGATE_FIRST) Closure spec) {
        def graph = new TaskExecutionGraph()
        spec.delegate = graph
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    private void recordOutputs() {
        output = outputWriter.toString()
        errorOutput = errorOutput.toString()
    }

    private GradleRunner newRunner(String... args) {
        outputWriter = new StringWriter()
        errorOutputWriter = new StringWriter()
        ArrayList<String> autoArgs = computeAutoArgs()
        def runner = GradleRunner.create()
                .forwardStdOutput(tee(new OutputStreamWriter(System.out), outputWriter))
                .forwardStdError(tee(new OutputStreamWriter(System.err), errorOutputWriter))
                .withPluginClasspath()
                .withProjectDir(testDirectory.toFile())
                .withArguments([*autoArgs, *args])
        if (gradleVersion) {
            runner.withGradleVersion(gradleVersion)
        }
        if (debug) {
            runner.withDebug(true)
        }
        runner
    }

    private ArrayList<String> computeAutoArgs() {
        List<String> autoArgs = [
                "-S",
        ]
        if (Boolean.getBoolean("config.cache")) {
            autoArgs << '--configuration-cache'
        }
        autoArgs
    }

    private static Writer tee(Writer one, Writer two) {
        return TeeWriter.of(one, two)
    }

    void fails(String... args) {
        try {
            result = newRunner(args)
                    .buildAndFail()
        } finally {
            recordOutputs()
        }
    }

    private class TaskExecutionGraph {
        void succeeded(String... tasks) {
            tasks.each { task ->
                contains(task)
                assert result.task(task).outcome == TaskOutcome.SUCCESS
            }
        }

        void contains(String... tasks) {
            tasks.each { task ->
                assert result.task(task) != null: "Expected to find task $task in the graph but it was missing"
            }
        }

        void doesNotContain(String... tasks) {
            tasks.each { task ->
                assert result.task(task) == null: "Task $task should be missing from the task graph but it was found with an outcome of ${result.task(task).outcome}"
            }
        }
    }
}
