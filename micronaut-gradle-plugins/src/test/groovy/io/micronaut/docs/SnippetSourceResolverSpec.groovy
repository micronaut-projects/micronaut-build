package io.micronaut.docs

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SnippetSourceResolverSpec extends Specification {

    @TempDir
    Path testDirectory

    void "finds snippet source files for aggregate guide"() {
        given:
        file("src/main/docs/guide/index.adoc") << "snippet::example.Foo,example.Bar[tags=body]\n"
        def javaFile = file("test-suite/src/test/java/example/Foo.java")
        def groovyFile = file("test-suite-groovy/src/test/groovy/example/Foo.groovy")
        def kotlinFile = file("test-suite-kotlin/src/test/kotlin/example/Foo.kt")
        def pythonFile = file("test-suite-python/src/test/python/example/Foo.py")
        def secondJavaFile = file("test-suite/src/test/java/example/Bar.java")

        expect:
        SnippetSourceResolver.findSnippetSourceFiles(
                testDirectory.resolve("src/main/docs").toFile(),
                testDirectory.toFile(),
                ""
        ) == [javaFile, secondJavaFile, pythonFile, kotlinFile, groovyFile] as Set
    }

    void "finds snippet source files for language specific guide"() {
        given:
        file("src/main/docs/guide/index.adoc") << "snippet::example.Foo[]\n"
        file("test-suite/src/test/java/example/Foo.java")
        def kotlinFile = file("test-suite-kotlin/src/test/kotlin/example/Foo.kt")

        expect:
        SnippetSourceResolver.findSnippetSourceFiles(
                testDirectory.resolve("src/main/docs").toFile(),
                testDirectory.toFile(),
                "kotlin"
        ) == [kotlinFile] as Set
    }

    void "honors snippet project source project base and python package rules"() {
        given:
        file("src/main/docs/guide/index.adoc") << """
snippet::example.Main[project=custom,source=main]
snippet::io.micronaut.Sample[project-base=base]
""".stripIndent()
        def customJavaFile = file("custom/src/main/java/example/Main.java")
        def baseJavaFile = file("base-java/src/test/java/io/micronaut/Sample.java")
        def basePythonFile = file("base-python/src/test/python/micronaut/Sample.py")
        def baseKotlinFile = file("base-kotlin/src/test/kotlin/io/micronaut/Sample.kt")
        def baseGroovyFile = file("base-groovy/src/test/groovy/io/micronaut/Sample.groovy")

        expect:
        SnippetSourceResolver.findSnippetSourceFiles(
                testDirectory.resolve("src/main/docs").toFile(),
                testDirectory.toFile(),
                ""
        ) == [customJavaFile, baseJavaFile, basePythonFile, baseKotlinFile, baseGroovyFile] as Set
    }

    private File file(String path) {
        File f = testDirectory.resolve(path).toFile()
        f.parentFile.mkdirs()
        if (!f.exists()) {
            assert f.createNewFile()
        }
        return f
    }
}
