package io.micronaut.build.graalvm

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class NativePropertiesWriterTest extends Specification {

    private Project project
    private TaskProvider<NativePropertiesWriter> writer
    private File nativeImagePropertiesFile

    def setup() {
        project = ProjectBuilder.builder().build()
        writer = project.tasks.register("nativePropertiesWriter", NativePropertiesWriter) {
            it.groupId.set("io.micronaut")
            it.artifactId.set("test")
            def outputDir = project.layout.buildDirectory.dir("native-image")
            it.outputDirectory.set(outputDir)
            nativeImagePropertiesFile = outputDir.get().file("META-INF/native-image/io.micronaut/test/native-image.properties").asFile
        }
    }

    def "can generate a native properties file with explicit contents"() {
        given:
        writer.get().contents.set("Args = --initialize-at-runtime=io.micronaut.Foo")

        when:
        writer.get().generateFile()

        then:
        nativeImagePropertiesFile.text == "Args = --initialize-at-runtime=io.micronaut.Foo\n"
    }

    def "can generate a native properties file with initialize at runtime"() {
        given:
        writer.get().initializeAtRuntime.addAll("com.Foo", "io.Bar")

        when:
        writer.get().generateFile()

        then:
        nativeImagePropertiesFile.text == "Args = --initialize-at-run-time=com.Foo,io.Bar\n"
    }

    def "can generate a native properties file with initialize at build time"() {
        given:
        writer.get().initializeAtBuildtime.addAll("com.Foo", "io.Bar")

        when:
        writer.get().generateFile()

        then:
        nativeImagePropertiesFile.text == "Args = --initialize-at-build-time=com.Foo,io.Bar\n"
    }

    def "can generate a native properties file with features"() {
        given:
        writer.get().features.addAll("com.Foo", "io.Bar")

        when:
        writer.get().generateFile()

        then:
        nativeImagePropertiesFile.text == "Args = --features=com.Foo,io.Bar\n"
    }

    def "can generate a native properties file with initialize and features"() {
        given:
        writer.get().initializeAtRuntime.addAll("com.Foo", "io.Bar")
        writer.get().initializeAtBuildtime.addAll("com.FooB", "io.BarB")
        writer.get().features.addAll("com.FooC", "io.BarC")

        when:
        writer.get().generateFile()

        then:
        nativeImagePropertiesFile.text == """Args = --initialize-at-build-time=com.FooB,io.BarB \\
       --initialize-at-run-time=com.Foo,io.Bar \\
       --features=com.FooC,io.BarC
"""
    }

    def "can generate a native properties file with initialize, features and custom entries"() {
        given:
        writer.get().initializeAtRuntime.addAll("com.Foo", "io.Bar")
        writer.get().initializeAtBuildtime.addAll("com.FooB", "io.BarB")
        writer.get().features.addAll("com.FooC", "io.BarC")
        writer.get().extraArguments.put("-H:APIFunctionPrefix", ["truffle_isolate"])

        when:
        writer.get().generateFile()

        then:
        nativeImagePropertiesFile.text == """Args = --initialize-at-build-time=com.FooB,io.BarB \\
       --initialize-at-run-time=com.Foo,io.Bar \\
       --features=com.FooC,io.BarC \\
       -H:APIFunctionPrefix=truffle_isolate
"""
    }


}
