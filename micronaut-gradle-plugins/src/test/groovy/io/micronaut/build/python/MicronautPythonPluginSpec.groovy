package io.micronaut.build.python

import io.micronaut.build.MicronautBuildExtension
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmVersion
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
        project.configurations.getByName("pythonCompileClasspath") in compilePython.classpath.from
        project.configurations.getByName("testPythonCompileClasspath") in compileTestPython.classpath.from
    }

    def "the python compile classpath declares the compile dependencies but resolves jars"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MicronautPythonPlugin)

        when:
        def classpath = project.configurations.getByName("testPythonCompileClasspath")

        then:
        classpath.canBeResolved
        !classpath.canBeConsumed
        classpath.extendsFrom.contains(project.configurations.getByName("testCompileClasspath"))
        classpath.attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == LibraryElements.JAR
        classpath.attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_API

        and: 'it requests every other attribute of the compile classpath, so that the same variants are selected'
        def compileAttributes = project.configurations.getByName("testCompileClasspath").attributes
        compileAttributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == LibraryElements.CLASSES
        compileAttributes.keySet().contains(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE)
        compileAttributes.keySet().contains(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
        classpath.attributes.keySet() == compileAttributes.keySet()
        (compileAttributes.keySet() - LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).each {
            assert classpath.attributes.getAttribute(it) == compileAttributes.getAttribute(it)
        }
    }

    def "the python compile classpath selects the variants of the compile classpath"() {
        given: 'a library which, like the OCI SDK modules of micronaut-oracle-cloud, publishes a custom variant instead of apiElements'
        def root = ProjectBuilder.builder().withName("root").build()
        def lib = ProjectBuilder.builder().withName("lib").withParent(root).build()
        lib.pluginManager.apply("java-library")
        lib.configurations.create("metadataElements") {
            it.canBeConsumed = true
            it.canBeResolved = false
            it.attributes {
                it.attribute(Usage.USAGE_ATTRIBUTE, lib.objects.named(Usage, Usage.JAVA_RUNTIME))
                it.attribute(Category.CATEGORY_ATTRIBUTE, lib.objects.named(Category, Category.LIBRARY))
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, lib.objects.named(LibraryElements, LibraryElements.JAR))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, lib.objects.named(Bundling, Bundling.EXTERNAL))
                // apiElements and runtimeElements of the java plugins don't carry this attribute
                it.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, lib.objects.named(TargetJvmEnvironment, TargetJvmEnvironment.STANDARD_JVM))
                it.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().majorVersion))
            }
        }
        def app = ProjectBuilder.builder().withName("app").withParent(root).build()
        app.pluginManager.apply(MicronautPythonPlugin)
        app.dependencies.add("implementation", lib)

        when:
        def javaVariants = selectedVariants(app.configurations.getByName("compileClasspath"))
        def pythonVariants = selectedVariants(app.configurations.getByName("pythonCompileClasspath"))

        then:
        javaVariants == ["metadataElements"]
        pythonVariants == javaVariants
    }

    private static List<String> selectedVariants(Configuration configuration) {
        configuration.incoming.resolutionResult.allComponents
            .findAll { it.id instanceof ProjectComponentIdentifier && it.id.projectPath != ":app" }
            .collectMany { it.variants*.displayName }
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

    def "compiler arguments of the extension apply to every compile task and a task can add its own"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MicronautPythonPlugin)
        def micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        def python = (micronautBuild as ExtensionAware).extensions.getByType(MicronautPythonExtension)
        def compilePython = project.tasks.named("compilePython", PythonCompile).get()
        def compileTestPython = project.tasks.named("compileTestPython", PythonCompile).get()

        expect: 'no arguments by default'
        compilePython.compilerArgs.get() == []
        compileTestPython.compilerArgs.get() == []

        when:
        python.compilerArgs.add("-Amicronaut.jsonschema.baseUri=https://example.com/schemas")
        compileTestPython.compilerArgs.add("-Amicronaut.openapi.project.dir=/tmp/project")

        then:
        compilePython.compilerArgs.get() == ["-Amicronaut.jsonschema.baseUri=https://example.com/schemas"]
        compileTestPython.compilerArgs.get() == [
                "-Amicronaut.jsonschema.baseUri=https://example.com/schemas",
                "-Amicronaut.openapi.project.dir=/tmp/project"
        ]

        when: 'a task replaces the arguments'
        compileTestPython.compilerArgs.set(["-Afoo=bar"])

        then:
        compileTestPython.compilerArgs.get() == ["-Afoo=bar"]
    }

    def "compiler arguments reach the compiler work action after the source root option"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MicronautPythonPlugin)
        def micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        def python = (micronautBuild as ExtensionAware).extensions.getByType(MicronautPythonExtension)
        python.compilerArgs.add("-Afoo=bar")
        def compilePython = project.tasks.named("compilePython", PythonCompile).get()
        compilePython.compilerArgs.add("-Abaz=qux")
        project.file("src/main/python").mkdirs()
        def projectDir = project.projectDir.toPath().toAbsolutePath().normalize().toString()

        when:
        def parameters = project.objects.newInstance(PythonCompileParameters)
        compilePython.configureWorkParameters(parameters)

        then:
        parameters.sourceDirs.get() == ["src/main/python"]
        parameters.sourceRoot.get() == projectDir
        parameters.destinationDir.get() == project.file("build/classes/python/main").absolutePath
        parameters.compilerArgs.get() == ["-Afoo=bar", "-Abaz=qux"]

        and: 'the work action puts the arguments after the option the plugin sets itself'
        PythonCompileWorkAction.compilerOptions(parameters.sourceRoot.get(), parameters.compilerArgs.get()) == [
                "-Amicronaut.python.source.root=" + projectDir,
                "-Afoo=bar",
                "-Abaz=qux"
        ]
    }

    def "the compiler is invoked with only the source root option when no arguments are configured"() {
        expect:
        PythonCompileWorkAction.compilerOptions("/project", []) == ["-Amicronaut.python.source.root=/project"]
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

    def "a test shard restricts the test task to its share of the test classes"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MicronautPythonPlugin)
        def micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        def python = (micronautBuild as ExtensionAware).extensions.getByType(MicronautPythonExtension)
        def test = project.tasks.named("test", org.gradle.api.tasks.testing.Test).get()
        def classesDir = project.file("build/classes/groovy/test")
        def classNames = (1..20).collect { "io/micronaut/Spec$it" }
        classNames.each { name ->
            new File(classesDir, "${name}.class").tap { parentFile.mkdirs() }.text = name
            new File(classesDir, "${name}\$Nested.class").text = name
        }
        test.testClassesDirs = project.files(classesDir)

        expect: "without a shard every class is a candidate"
        test.candidateClassFiles.files.size() == classNames.size() * 2
        test.failOnNoDiscoveredTests.get()

        when:
        python.testShard.set("2/3")
        def candidates = test.candidateClassFiles.files.collect { classesDir.toPath().relativize(it.toPath()).toString() }

        then: "a shard is a strict, non-empty subset in which nested classes follow their declaring class"
        !candidates.empty
        candidates.size() < classNames.size() * 2
        candidates.every { it.startsWith("io/micronaut/") }
        candidates.findAll { it.contains('$') }*.replaceAll(/\$.*/, '.class').toSet() == candidates.findAll { !it.contains('$') }.toSet()

        and: "a shard which happens to get no test class is not an error"
        !test.failOnNoDiscoveredTests.get()
    }

    def "a build can shard the tests of a project without python sources"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        def test = project.tasks.named("test", org.gradle.api.tasks.testing.Test).get()
        def classesDir = project.file("build/classes/java/test")
        (1..20).each { new File(classesDir, "io/micronaut/Test${it}.class").tap { parentFile.mkdirs() }.text = "$it" }
        test.testClassesDirs = project.files(classesDir)

        when: "the property is not set"
        MicronautPythonPlugin.shardTests(project)

        then:
        test.candidateClassFiles.files.size() == 20
        test.failOnNoDiscoveredTests.get()
    }

    def "a shard narrows the includes of a test task instead of widening them"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MicronautPythonPlugin)
        def micronautBuild = project.extensions.getByType(MicronautBuildExtension)
        def python = (micronautBuild as ExtensionAware).extensions.getByType(MicronautPythonExtension)
        def test = project.tasks.named("test", org.gradle.api.tasks.testing.Test).get()
        def classesDir = project.file("build/classes/java/test")
        def included = (1..20).collect { "io/micronaut/included/Test${it}.class" }
        def excluded = (1..20).collect { "io/micronaut/excluded/Test${it}.class" }
        (included + excluded).each { new File(classesDir, it).tap { parentFile.mkdirs() }.text = it }
        test.testClassesDirs = project.files(classesDir)
        test.include("io/micronaut/included/**")
        def shardOf = { String path -> (1..3).find { new TestShard(it, 3).includesClassFile(path) } }

        when:
        python.testShard.set("2/3")
        def candidates = test.candidateClassFiles.files.collect { classesDir.toPath().relativize(it.toPath()).toString() }

        then:
        !candidates.empty
        candidates.toSet() == included.findAll { shardOf(it) == 2 }.toSet()
    }

    def "every test class belongs to exactly one shard"() {
        given:
        def classes = (1..200).collect { "io/micronaut/some/pkg/SomeSpec${it}.class" }
        def shards = (1..count).collect { new TestShard(it, count) }

        expect:
        classes.every { path -> shards.count { it.includesClassFile(path) } == 1 }
        shards.every { shard -> classes.any { shard.includesClassFile(it) } }

        where:
        count << [1, 2, 3, 4, 7]
    }

    def "shard membership is decided by the top level class and applies to files only"() {
        given:
        def shard = (1..4).collect { new TestShard(it, 4) }.find { it.includesClassFile("io/micronaut/FooSpec.class") }
        def directory = Stub(org.gradle.api.file.FileTreeElement) { isDirectory() >> true }

        expect:
        shard.includesClassFile('io/micronaut/FooSpec$Nested.class')
        shard.includesClassFile('io/micronaut/FooSpec$Nested$Deeper.class')
        shard.includesClassFile('io/micronaut/FooSpec$_closure1.class')
        shard.isSatisfiedBy(directory)
        // a '$' in a package name is not a nested class marker
        new TestShard(1, 1).includesClassFile('io/mi$cro/FooSpec.class')
    }

    def "the shard property has the form index/count"() {
        expect:
        TestShard.parse("2/4") == new TestShard(2, 4)
        TestShard.parse(" 1 / 1 ") == new TestShard(1, 1)

        when:
        TestShard.parse(value)

        then:
        def e = thrown(org.gradle.api.InvalidUserDataException)
        e.message.contains(message)

        where:
        value   | message
        "3"     | "must have the form <index>/<count>"
        "a/b"   | "must have the form <index>/<count>"
        "1/2/3" | "must have the form <index>/<count>"
        "0/4"   | "between 1 and 4"
        "5/4"   | "between 1 and 4"
        "1/0"   | "at least 1"
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

    def "python sources see the java output of their own source set and of main"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MicronautPythonPlugin)
        def sourceSets = project.extensions.getByType(SourceSetContainer)

        when:
        def compileTestPython = project.tasks.named("compileTestPython", PythonCompile).get()
        def files = compileTestPython.classpath.files

        then:
        sourceSets.getByName("test").java.classesDirectory.get().asFile in files
        sourceSets.getByName("main").java.classesDirectory.get().asFile in files
        sourceSets.getByName("test").output.resourcesDir in files
        sourceSets.getByName("main").output.resourcesDir in files
        project.file("build/classes/python/main") in files
        compileTestPython.taskDependencies.getDependencies(compileTestPython)*.name.containsAll(["compileTestJava", "compileJava", "compilePython", "processTestResources", "processResources"])
    }
}
