package io.micronaut.build.python

import io.micronaut.build.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

class PythonSourceFilteringFunctionalTest extends AbstractFunctionalTest {

    def "include and exclude patterns of the python source directory set are honoured"() {
        given:
        withFakeCompiler()
        settingsFile << """
            rootProject.name = 'python-filtering'
            include 'fake-compiler'
        """
        buildFile << """
            plugins {
                id 'io.micronaut.build.internal.python'
            }

            micronautBuild.python.compilerVersion = ''

            dependencies {
                pyronautCompiler project(':fake-compiler')
            }

            sourceSets {
                test {
                    python {
                        exclude 'micronaut/docs/jsonschema/**'
                    }
                }
                jsonSchemaTest {
                    python {
                        srcDirs = ['src/test/python']
                        include 'micronaut/docs/jsonschema/**'
                    }
                }
            }
        """
        file("src/main/python/micronaut/app.py") << "print('app')\n"
        file("src/test/python/micronaut/docs/hello.py") << "print('hello')\n"
        file("src/test/python/micronaut/docs/jsonschema/schema_sample.py") << "print('schema')\n"

        when:
        run 'compilePython', 'compileTestPython', 'compileJsonSchemaTestPython'

        then: 'unfiltered sources are compiled from their source directory'
        compiled("main") == [
                "src=src/main/python",
                "micronaut/app.py"
        ]

        and: 'the two source sets compile disjoint parts of the same directory'
        compiled("test") == [
                "src=build/tmp/compileTestPython/python-sources/0",
                "micronaut/docs/hello.py"
        ]
        compiled("jsonSchemaTest") == [
                "src=build/tmp/compileJsonSchemaTestPython/python-sources/0",
                "micronaut/docs/jsonschema/schema_sample.py"
        ]

        when: 'a source excluded from the test source set is added'
        file("src/test/python/micronaut/docs/jsonschema/other_sample.py") << "print('other')\n"
        run 'compileTestPython', 'compileJsonSchemaTestPython'

        then:
        result.task(':compileTestPython').outcome == TaskOutcome.UP_TO_DATE
        result.task(':compileJsonSchemaTestPython').outcome == TaskOutcome.SUCCESS
        compiled("jsonSchemaTest") == [
                "src=build/tmp/compileJsonSchemaTestPython/python-sources/0",
                "micronaut/docs/jsonschema/other_sample.py",
                "micronaut/docs/jsonschema/schema_sample.py"
        ]
    }

    private List<String> compiled(String sourceSet) {
        file("build/classes/python/$sourceSet/compiled-sources.txt").readLines()
    }

    /**
     * Writes a project providing a stand-in for the Pyronaut compiler: it has the builder API the work action
     * calls and records the source directories it is given and the Python files it finds below them.
     */
    private void withFakeCompiler() {
        file("fake-compiler/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """
        file("fake-compiler/src/main/java/io/micronaut/python/compiler/PyronautCompiler.java") << '''
            package io.micronaut.python.compiler;

            import java.io.File;
            import java.io.IOException;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.stream.Stream;

            public class PyronautCompiler {
                private final Builder builder;

                private PyronautCompiler(Builder builder) {
                    this.builder = builder;
                }

                public static Builder builder() {
                    return new Builder();
                }

                public void compile() throws IOException {
                    String root = null;
                    for (String option : builder.options) {
                        if (option.startsWith("-Amicronaut.python.source.root=")) {
                            root = option.substring(option.indexOf('=') + 1);
                        }
                    }
                    List<String> lines = new ArrayList<>();
                    for (String src : builder.pythonSrc.split(",")) {
                        lines.add("src=" + src.replace(File.separatorChar, '/'));
                        Path dir = Path.of(root).resolve(src);
                        try (Stream<Path> files = Files.walk(dir)) {
                            files.filter(f -> f.toString().endsWith(".py"))
                                .map(f -> dir.relativize(f).toString().replace(File.separatorChar, '/'))
                                .sorted()
                                .forEach(lines::add);
                        }
                    }
                    Files.createDirectories(builder.targetDir.toPath());
                    Files.write(builder.targetDir.toPath().resolve("compiled-sources.txt"), lines);
                }

                public static class Builder {
                    private String pythonSrc;
                    private File targetDir;
                    private List<String> options = List.of();

                    public Builder pythonSrc(String pythonSrc) {
                        this.pythonSrc = pythonSrc;
                        return this;
                    }

                    public Builder targetDir(File targetDir) {
                        this.targetDir = targetDir;
                        return this;
                    }

                    public Builder classpath(List<File> classpath) {
                        return this;
                    }

                    public Builder annotationProcessorPath(List<File> annotationProcessorPath) {
                        return this;
                    }

                    public Builder options(List<String> options) {
                        this.options = options;
                        return this;
                    }

                    public PyronautCompiler build() {
                        return new PyronautCompiler(this);
                    }
                }
            }
        '''
    }
}
