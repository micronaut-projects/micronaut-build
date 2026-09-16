package io.micronaut.build.python

import io.micronaut.build.MicronautBuildExtension
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class MicronautPythonPluginSpec extends Specification {

    def "adds python source directories and compile tasks to every source set"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.pluginManager.apply(MicronautPythonPlugin)
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def mainPython = sourceSets.getByName("main").extensions.getByName("python") as SourceDirectorySet
        def testPython = sourceSets.getByName("test").extensions.getByName("python") as SourceDirectorySet

        then:
        mainPython.srcDirs == [project.file("src/main/python")] as Set
        testPython.srcDirs == [project.file("src/test/python")] as Set

        and:
        project.tasks.named("compilePython", PythonCompile).present
        project.tasks.named("compileTestPython", PythonCompile).present

        and: 'the compiled python classes are part of the source set output'
        project.file("build/classes/python/main") in sourceSets.getByName("main").output.classesDirs.files
        project.file("build/classes/python/test") in sourceSets.getByName("test").output.classesDirs.files
    }

    def "compile tasks are wired to the pyronaut compiler and the compile classpath"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MicronautPythonPlugin)

        when:
        def compilePython = project.tasks.named("compilePython", PythonCompile).get()
        def compileTestPython = project.tasks.named("compileTestPython", PythonCompile).get()

        then:
        compilePython.destinationDir.get().asFile == project.file("build/classes/python/main")
        compileTestPython.destinationDir.get().asFile == project.file("build/classes/python/test")
        project.configurations.getByName(MicronautPythonPlugin.PYRONAUT_COMPILER_CLASSPATH_CONFIGURATION) in compilePython.compilerClasspath.from
        project.configurations.getByName("compileClasspath") in compilePython.classpath.from
        project.configurations.getByName("testCompileClasspath") in compileTestPython.classpath.from
    }

    def "the pyronaut compiler is resolved from the micronaut version"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.extensions.extraProperties.set("micronautVersion", "5.2.2")

        when:
        project.pluginManager.apply(MicronautPythonPlugin)
        def compiler = project.configurations.getByName(MicronautPythonPlugin.PYRONAUT_COMPILER_CONFIGURATION)
        def classpath = project.configurations.getByName(MicronautPythonPlugin.PYRONAUT_COMPILER_CLASSPATH_CONFIGURATION)

        then:
        !compiler.canBeResolved
        !compiler.canBeConsumed
        classpath.canBeResolved
        !classpath.canBeConsumed
        classpath.extendsFrom.contains(compiler)
        compiler.dependencies.collect { "${it.group}:${it.name}:${it.version}".toString() } as Set == [
                "io.micronaut:micronaut-inject-python:5.2.2",
                "io.micronaut:micronaut-context-python:5.2.2"
        ] as Set
    }

    def "no compiler dependency is added when the compiler version is empty"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.extensions.extraProperties.set("micronautVersion", "5.2.2")

        when:
        project.pluginManager.apply(MicronautPythonPlugin)
        def micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        def python = (micronautBuild as ExtensionAware).extensions.getByType(MicronautPythonExtension)
        python.compilerVersion.set("")

        then:
        project.configurations.getByName(MicronautPythonPlugin.PYRONAUT_COMPILER_CONFIGURATION).dependencies.empty
    }

    def "no compiler dependency is added when the micronaut version is unknown"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.pluginManager.apply(MicronautPythonPlugin)

        then:
        project.configurations.getByName(MicronautPythonPlugin.PYRONAUT_COMPILER_CONFIGURATION).dependencies.empty
    }

    def "compile task names are derived from the source set name"() {
        expect:
        MicronautPythonPlugin.compileTaskName("main") == "compilePython"
        MicronautPythonPlugin.compileTaskName("test") == "compileTestPython"
        MicronautPythonPlugin.compileTaskName("integrationTest") == "compileIntegrationTestPython"
    }

    def "python tests only run when the python-ci property is set"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.pluginManager.apply(MicronautPythonPlugin)
        def micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        def python = (micronautBuild as ExtensionAware).extensions.getByType(MicronautPythonExtension)
        def test = project.tasks.named("test", org.gradle.api.tasks.testing.Test).get()

        then:
        !python.testsEnabled.get()
        !test.onlyIf.isSatisfiedBy(test)

        when:
        python.testsEnabled.set(true)

        then:
        test.onlyIf.isSatisfiedBy(test)
    }

    def "the root pythonCheck task aggregates the check tasks of python projects"() {
        given:
        def root = ProjectBuilder.builder().withName("root").build()
        def python = ProjectBuilder.builder().withName("test-suite-python").withParent(root).build()
        def other = ProjectBuilder.builder().withName("other-python").withParent(root).build()

        when:
        python.pluginManager.apply(MicronautPythonPlugin)
        other.pluginManager.apply(MicronautPythonPlugin)
        def pythonCheck = root.tasks.named(MicronautPythonPlugin.PYTHON_CHECK_TASK_NAME).get()

        then:
        pythonCheck.group == "verification"
        pythonCheck.taskDependencies.getDependencies(pythonCheck)*.path as Set == [":test-suite-python:check", ":other-python:check"] as Set
    }

    def "a pythonCheck task registered by the root project is reused"() {
        given:
        def root = ProjectBuilder.builder().withName("root").build()
        root.tasks.register(MicronautPythonPlugin.PYTHON_CHECK_TASK_NAME) {
            it.description = "custom"
        }
        def python = ProjectBuilder.builder().withName("test-suite-python").withParent(root).build()

        when:
        python.pluginManager.apply(MicronautPythonPlugin)
        def pythonCheck = root.tasks.named(MicronautPythonPlugin.PYTHON_CHECK_TASK_NAME).get()

        then:
        pythonCheck.description == "custom"
        pythonCheck.taskDependencies.getDependencies(pythonCheck)*.path == [":test-suite-python:check"]
    }
}
