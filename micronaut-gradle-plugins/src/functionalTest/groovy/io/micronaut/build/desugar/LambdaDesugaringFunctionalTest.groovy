package io.micronaut.build.desugar

import io.micronaut.build.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

import java.lang.classfile.Attributes
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassModel
import java.lang.classfile.instruction.InvokeDynamicInstruction
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class LambdaDesugaringFunctionalTest extends AbstractFunctionalTest {

    private static final String ON = '-PmicronautBuild.desugarLambdas=true'
    private static final String JAR = 'lib/build/libs/lib-1.0.0-SNAPSHOT.jar'

    void "rewrites and keeps the right sites, and only the jar holds the generated classes"() {
        given:
        withSample('test-desugar-lambdas')

        when:
        run ':lib:jar', ON

        then:
        tasks {
            succeeded ':lib:desugarLambdas', ':lib:jar'
            doesNotContain ':api:desugarLambdas', ':optional:desugarLambdas'
        }

        and: 'the report names every site'
        Map<String, String> sites = sites()
        sites['captureFree#0'] == 'rewritten'
        sites['capturing#0'] == 'rewritten'
        sites['methodReference#0'] == 'rewritten'
        sites['constructorReference#0'] == 'rewritten'
        sites['privateImplementation#0'] == 'rewritten'
        sites['dependencyInterface#0'] == 'rewritten'
        sites['inherited#0'] == 'rewritten'
        sites['serializable#0'] == 'altMetafactory'
        sites['$deserializeLambda$#0'] == 'altMetafactory'
        sites['compileOnly#0'] == 'unresolvedType'
        report().contains('kept.unresolvedType.compileOnly=1')
        report().contains('rewrittenSites=7')
        // captureFree and constructorReference share R0, privateImplementation and inherited share R4
        report().contains('generatedClasses=5')

        and: "the compiler's output is untouched"
        Path compiled = file('lib/build/classes/java/main').toPath()
        !Files.exists(compiled.resolve('demo/Host$$Lambda$R0.class'))
        lambdaSites(Files.readAllBytes(compiled.resolve('demo/Host.class'))) == 10

        and: 'the jar holds the rewritten host and the generated classes, with the host source file'
        Map<String, byte[]> jar = entries(file(JAR))
        lambdaSites(jar['demo/Host.class']) == 3
        jar.keySet().findAll { it.contains('$$Lambda$') }.sort() == [0, 1, 2, 4, 5].collect { "demo/Host\$\$Lambda\$R${it}.class".toString() }
        sourceFile(jar['demo/Host$$Lambda$R0.class']) == 'Host.java'

        and: 'native image initializes the generated classes at build time'
        new String(jar['META-INF/native-image/io.micronaut.dummy/lib/desugared-lambdas/native-image.properties']).contains('demo.Host$$Lambda$R5')

        and: 'the classes of other compilers go into the jar unchanged'
        jar['demo/GroovyHelper.class'] == Files.readAllBytes(file('lib/build/classes/groovy/main/demo/GroovyHelper.class').toPath())
    }

    void "without the property nothing changes"() {
        given:
        withSample('test-desugar-lambdas')

        when:
        run ':lib:jar'

        then:
        tasks {
            succeeded ':lib:jar'
            doesNotContain ':lib:desugarLambdas'
        }
        Map<String, byte[]> jar = entries(file(JAR)).findAll { it.key.endsWith('.class') }
        Map<String, byte[]> compiled = classes('lib/build/classes/java/main') + classes('lib/build/classes/groovy/main')
        jar.keySet() == compiled.keySet()
        jar.every { name, bytes -> bytes == compiled[name] }
    }

    void "two clean builds produce identical jars and a rerun comes from the build cache"() {
        given:
        withSample('test-desugar-lambdas')

        when:
        run ':lib:jar', ON, '--build-cache'
        byte[] first = Files.readAllBytes(file(JAR).toPath())
        run ':lib:clean'
        run ':lib:jar', ON

        then:
        result.task(':lib:desugarLambdas').outcome == TaskOutcome.SUCCESS
        Files.readAllBytes(file(JAR).toPath()) == first

        when:
        run ':lib:clean'
        run ':lib:jar', ON, '--build-cache'

        then:
        result.task(':lib:desugarLambdas').outcome == TaskOutcome.FROM_CACHE
        Files.readAllBytes(file(JAR).toPath()) == first
    }

    void "the public and protected API of the jar is the same with the switch on and off"() {
        given:
        withSample('test-desugar-lambdas')

        when:
        run ':lib:jar'
        Map<String, List<String>> off = api(entries(file(JAR)))
        run ':lib:jar', ON
        Map<String, List<String>> on = api(entries(file(JAR)))

        then:
        result.task(':lib:desugarLambdas').outcome == TaskOutcome.SUCCESS
        off.keySet().containsAll(['demo/Host', 'demo/GroovyHelper'])
        on == off
    }

    void "runs in a worker on the module's toolchain"() {
        given:
        withSample('test-desugar-lambdas')
        environment['USE_GRADLE_TOOLCHAINS'] = 'true'
        file('lib/build.gradle') << """
            micronautBuild {
                javaVersion = ${System.getProperty('CURRENT_JDK')}
            }
        """

        when:
        run ':lib:jar', ON

        then:
        result.task(':lib:desugarLambdas').outcome == TaskOutcome.SUCCESS
        report().contains('rewrittenSites=7')
        lambdaSites(entries(file(JAR))['demo/Host.class']) == 3
    }

    private String report() {
        file('lib/build/reports/desugarLambdas/main.txt').text
    }

    private Map<String, String> sites() {
        Map<String, String> sites = [:]
        report().readLines().findAll { it.startsWith('site\tdemo/Host\t') }.each { line ->
            String[] parts = line.split('\t')
            sites[parts[2].substring(0, parts[2].indexOf('(')) + '#' + parts[3]] = parts[4] == 'rewritten' ? 'rewritten' : parts[5]
        }
        sites
    }

    private Map<String, byte[]> classes(String directory) {
        Path root = file(directory).toPath()
        Map<String, byte[]> classes = [:]
        Files.walk(root).withCloseable { stream ->
            stream.filter { Files.isRegularFile(it) }.each {
                classes[root.relativize(it).toString().replace('\\', '/')] = Files.readAllBytes(it)
            }
        }
        classes
    }

    private static Map<String, byte[]> entries(File jar) {
        Map<String, byte[]> entries = [:]
        new ZipFile(jar).withCloseable { zip ->
            zip.entries().each { entry ->
                if (!entry.directory) {
                    entries[entry.name] = zip.getInputStream(entry).bytes
                }
            }
        }
        entries
    }

    private static int lambdaSites(byte[] bytes) {
        ClassModel model = ClassFile.of().parse(bytes)
        int count = 0
        model.methods().each { method ->
            method.code().ifPresent { code ->
                code.elementList().each {
                    if (it instanceof InvokeDynamicInstruction && it.bootstrapMethod().owner().descriptorString() == 'Ljava/lang/invoke/LambdaMetafactory;') {
                        count++
                    }
                }
            }
        }
        count
    }

    private static String sourceFile(byte[] bytes) {
        ClassFile.of().parse(bytes).findAttribute(Attributes.sourceFile()).map { it.sourceFile().stringValue() }.orElse(null)
    }

    /**
     * The public and protected API of every class of a jar that is itself public or protected: its flags,
     * supertypes and members.
     */
    private static Map<String, List<String>> api(Map<String, byte[]> jar) {
        Map<String, List<String>> api = [:]
        jar.findAll { it.key.endsWith('.class') }.each { name, bytes ->
            ClassModel model = ClassFile.of().parse(bytes)
            int flags = model.flags().flagsMask()
            if ((flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) == 0) {
                return
            }
            List<String> members = []
            members << "class ${flags} ${model.superclass().map { it.asInternalName() }.orElse('')} ${model.interfaces()*.asInternalName()}".toString()
            model.fields().each {
                if ((it.flags().flagsMask() & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0) {
                    members << "field ${it.flags().flagsMask()} ${it.fieldName().stringValue()} ${it.fieldType().stringValue()}".toString()
                }
            }
            model.methods().each {
                if ((it.flags().flagsMask() & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0) {
                    members << "method ${it.flags().flagsMask()} ${it.methodName().stringValue()}${it.methodType().stringValue()}".toString()
                }
            }
            api[model.thisClass().asInternalName()] = members.sort()
        }
        api
    }
}
