package io.micronaut.build.desugar.internal

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.ToolProvider
import java.io.ObjectStreamClass
import java.lang.classfile.Attributes
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassModel
import java.lang.classfile.instruction.InvokeDynamicInstruction
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.IntBinaryOperator
import java.util.function.Function
import java.util.function.Supplier

class ModuleDesugaringSpec extends Specification {

    private static final Map<String, String> OPTIONAL_SOURCES = [
            'opt/OptionalType.java': '''
package opt;
public class OptionalType {
    private final String name;
    public OptionalType(String name) { this.name = name; }
    public String name() { return name; }
}
''']

    private static final Map<String, String> RUNTIME_SOURCES = [
            'dep/Transformer.java': '''
package dep;
@FunctionalInterface
public interface Transformer<T, R> {
    R transform(T value);
}
''']

    private static final Map<String, String> MODULE_SOURCES = [
            'demo/Host.java': '''
package demo;

import dep.Transformer;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;
import opt.OptionalType;

public class Host implements Serializable {
    private final String prefix;

    public Host(String prefix) {
        this.prefix = prefix;
    }

    public static Supplier<String> captureFree() {
        return () -> "free";
    }

    public Function<String, String> capturing() {
        return s -> prefix + s;
    }

    public static Function<Integer, String> methodReference() {
        return Integer::toHexString;
    }

    public static Supplier<List<String>> constructorReference() {
        return ArrayList::new;
    }

    public static Runnable serializable() {
        return (Runnable & Serializable) () -> { };
    }

    private String secret() {
        return "secret:" + prefix;
    }

    public Supplier<String> privateImplementation() {
        return this::secret;
    }

    public static IntBinaryOperator primitives() {
        return Math::max;
    }

    public static Transformer<String, Integer> runtimeInterface() {
        return String::length;
    }

    public static Supplier<String> compileOnly(OptionalType type) {
        return () -> type.name();
    }

    public static Supplier<String> failing() {
        return () -> {
            throw new IllegalStateException("boom");
        };
    }

    public Function<String, String> capturingAgain() {
        return s -> s + prefix;
    }

    public static Supplier<String> withLong(long n) {
        return () -> "n" + n;
    }

    public static Supplier<String> withLongAgain(long n) {
        return () -> n + "m";
    }

    public static class Inner {
        public Supplier<String> get() {
            return () -> "inner";
        }
    }
}
''',
            'demo/Many.java': '''
package demo;

import java.util.List;
import java.util.function.Supplier;

public class Many {
    public static List<Supplier<String>> all() {
        return List.of(
            () -> "0",
            () -> "1",
            () -> "2",
            () -> "3",
            () -> "4",
            () -> "5",
            () -> "6",
            () -> "7",
            () -> "8",
            () -> "9",
            () -> "10",
            () -> "11",
            () -> "12",
            () -> "13",
            () -> "14",
            () -> "15",
            () -> "16",
            () -> "17",
            () -> "18",
            () -> "19",
            () -> "20",
            () -> "21",
            () -> "22",
            () -> "23",
            () -> "24",
            () -> "25",
            () -> "26",
            () -> "27",
            () -> "28",
            () -> "29",
            () -> "30",
            () -> "31",
            () -> "32",
            () -> "33",
            () -> "34",
            () -> "35",
            () -> "36",
            () -> "37",
            () -> "38",
            () -> "39");
    }
}
''']

    @TempDir
    @Shared
    Path work

    @Shared
    Path optional
    @Shared
    Path runtime
    @Shared
    Path classes
    @Shared
    Path output
    @Shared
    Path report
    @Shared
    String summary

    def setupSpec() {
        optional = compile('optional', OPTIONAL_SOURCES, [])
        runtime = compile('runtime', RUNTIME_SOURCES, [])
        classes = compile('classes', MODULE_SOURCES, [optional, runtime])
        output = work.resolve('output')
        report = work.resolve('report.txt')
        summary = ModuleDesugaring.run(classes, [runtime], [optional, runtime], output, report, 'demo/demo-module')
    }

    def "rewrites the sites it can and keeps the others by reason"() {
        when:
        Map<String, String> sites = [:]
        report.readLines().findAll { it.startsWith('site\t') }.each {
            def parts = it.split('\t')
            sites[parts[1] + '.' + parts[2].substring(0, parts[2].indexOf('(')) + '#' + parts[3]] = parts[4] + (parts.length > 5 ? ':' + parts[5] : '')
        }

        then:
        sites['demo/Host.captureFree#0'] == 'rewritten:demo/Host$$Lambda$R0'
        sites['demo/Host.capturing#0'] == 'rewritten:demo/Host$$Lambda$R1'
        sites['demo/Host.methodReference#0'] == 'rewritten:demo/Host$$Lambda$R2'
        sites['demo/Host.serializable#0'] == 'kept:altMetafactory'
        sites['demo/Host.$deserializeLambda$#0'] == 'kept:altMetafactory'
        sites['demo/Host.privateImplementation#0'] == 'rewritten:demo/Host$$Lambda$R4'
        sites['demo/Host.primitives#0'] == 'rewritten:demo/Host$$Lambda$R5'
        sites['demo/Host.runtimeInterface#0'] == 'rewritten:demo/Host$$Lambda$R6'
        sites['demo/Host.compileOnly#0'] == 'kept:unresolvedType'
        sites['demo/Host$Inner.get#0'] == 'rewritten:demo/Host$Inner$$Lambda$R0'

        and: 'sites of one host with the same interface and captured types share the class of the first'
        sites['demo/Host.constructorReference#0'] == 'rewritten:demo/Host$$Lambda$R0'
        sites['demo/Host.failing#0'] == 'rewritten:demo/Host$$Lambda$R0'
        sites['demo/Host.capturingAgain#0'] == 'rewritten:demo/Host$$Lambda$R1'
        sites['demo/Host.withLong#0'] == 'rewritten:demo/Host$$Lambda$R9'
        sites['demo/Host.withLongAgain#0'] == 'rewritten:demo/Host$$Lambda$R9'
        (0..37).every { sites["demo/Many.all#${it}".toString()] == 'rewritten:demo/Many$$Lambda$R0' }
        (38..39).every { sites["demo/Many.all#${it}".toString()] == 'rewritten:demo/Many$$Lambda$R38' }

        and: 'the report counts sites and classes apart'
        report.readLines().containsAll([
                'rewrittenSites=52',
                'generatedClasses=10',
                'kept.altMetafactory=2',
                'kept.unresolvedType=1',
                'kept.unresolvedType.compileOnly=1',
        ])
        report.text.contains('kept\tunresolvedType\topt/OptionalType')
        summary.startsWith('Desugared 52 lambda call sites into 10 generated classes')
    }

    def "the desugared classes behave as compiled"() {
        given:
        def loader = new URLClassLoader([output, runtime, optional].collect { it.toUri().toURL() } as URL[], (ClassLoader) null)
        Class<?> host = loader.loadClass('demo.Host')
        def instance = host.getConstructor(String).newInstance('p:')

        expect:
        (host.getMethod('captureFree').invoke(null) as Supplier).get() == 'free'
        host.getMethod('captureFree').invoke(null).is(host.getMethod('captureFree').invoke(null))
        (host.getMethod('capturing').invoke(instance) as Function).apply('x') == 'p:x'
        (host.getMethod('methodReference').invoke(null) as Function).apply(255) == 'ff'
        (host.getMethod('constructorReference').invoke(null) as Supplier).get() == []
        (host.getMethod('privateImplementation').invoke(instance) as Supplier).get() == 'secret:p:'
        (host.getMethod('primitives').invoke(null) as IntBinaryOperator).applyAsInt(3, 7) == 7
        loader.loadClass('dep.Transformer').getMethod('transform', Object)
                .invoke(host.getMethod('runtimeInterface').invoke(null), 'four') == 4
        (loader.loadClass('demo.Host$Inner').getMethod('get')
                .invoke(loader.loadClass('demo.Host$Inner').getConstructor().newInstance()) as Supplier).get() == 'inner'
        def type = loader.loadClass('opt.OptionalType').getConstructor(String).newInstance('n')
        (host.getMethod('compileOnly', type.class).invoke(null, type) as Supplier).get() == 'n'
        (host.getMethod('capturingAgain').invoke(instance) as Function).apply('x') == 'xp:'
        (host.getMethod('withLong', long).invoke(null, 5L) as Supplier).get() == 'n5'
        (host.getMethod('withLongAgain', long).invoke(null, 6L) as Supplier).get() == '6m'
        (loader.loadClass('demo.Many').getMethod('all').invoke(null) as List<Supplier>)*.get() == (0..39)*.toString()

        and: 'rewritten sites yield named, synthetic classes; kept ones stay hidden'
        def free = host.getMethod('captureFree').invoke(null)
        free.class.name == 'demo.Host$$Lambda$R0'
        !free.class.hidden
        free.class.synthetic
        free.class.nestHost == host
        host.getMethod('serializable').invoke(null).class.hidden
        host.getMethod('compileOnly', type.class).invoke(null, type).class.hidden

        cleanup:
        loader?.close()
    }

    def "sites that share a class keep their own behaviour and capture-free instances"() {
        given:
        def loader = new URLClassLoader([output, runtime, optional].collect { it.toUri().toURL() } as URL[], (ClassLoader) null)
        Class<?> host = loader.loadClass('demo.Host')
        def instance = host.getConstructor(String).newInstance('p:')
        def free = host.getMethod('captureFree').invoke(null)
        def list = host.getMethod('constructorReference').invoke(null)
        def failing = host.getMethod('failing').invoke(null)
        def many = loader.loadClass('demo.Many').getMethod('all').invoke(null) as List
        def manyAgain = loader.loadClass('demo.Many').getMethod('all').invoke(null) as List

        expect: 'capture-free sites share a class, each still yields one instance of its own'
        [free, list, failing]*.class.name.unique() == ['demo.Host$$Lambda$R0']
        free.is(host.getMethod('captureFree').invoke(null))
        list.is(host.getMethod('constructorReference').invoke(null))
        failing.is(host.getMethod('failing').invoke(null))
        !free.is(list) && !list.is(failing) && !free.is(failing)
        (free as Supplier).get() == 'free'
        (list as Supplier).get() == []
        (0..39).every { many[it].is(manyAgain[it]) }
        many.toSet().size() == 40
        many*.class.name.unique() == ['demo.Many$$Lambda$R0', 'demo.Many$$Lambda$R38']

        and: 'capturing sites share a class and get a new instance per evaluation, with their own captures'
        def first = host.getMethod('capturing').invoke(instance)
        def second = host.getMethod('capturingAgain').invoke(instance)
        first.class == second.class
        first.class.name == 'demo.Host$$Lambda$R1'
        !first.is(host.getMethod('capturing').invoke(instance))
        (first as Function).apply('a') == 'p:a'
        (second as Function).apply('a') == 'ap:'
        def n = host.getMethod('withLong', long).invoke(null, Long.MAX_VALUE)
        def m = host.getMethod('withLongAgain', long).invoke(null, -1L)
        n.class == m.class
        (n as Supplier).get() == 'n' + Long.MAX_VALUE
        (m as Supplier).get() == '-1m'

        cleanup:
        loader?.close()
    }

    def "a shared class takes sites until its interface method would pass FreqInlineSize"() {
        given:
        Map<String, Integer> dispatch = [:]
        Map<String, Integer> fields = [:]
        files(output).findAll { it.contains('$$Lambda$R') }.each { name ->
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(output.resolve(name)))
            def sam = model.methods().find { (it.flags().flagsMask() & ClassFile.ACC_PUBLIC) != 0 }
            dispatch[model.thisClass().asInternalName()] = sam.findAttribute(Attributes.code()).get().codeLength()
            fields[model.thisClass().asInternalName()] = model.fields().size()
        }

        expect: '38 cases of 8 bytes and the 20 bytes of the dispatch make 324, one more would make 332'
        dispatch['demo/Many$$Lambda$R0'] == 324
        dispatch['demo/Many$$Lambda$R38'] == 20 + 2 * 8
        dispatch.values().every { it <= LambdaDesugarer.MAX_DISPATCH_BYTES }
        LambdaDesugarer.MAX_DISPATCH_BYTES == 325

        and: 'only shared classes have a tag, and capture-free ones an array of instances'
        fields['demo/Many$$Lambda$R0'] == 2
        fields['demo/Host$$Lambda$R1'] == 2
        fields['demo/Host$$Lambda$R9'] == 2
        fields['demo/Host$$Lambda$R2'] == 1
        dispatch.keySet().sort() == ['demo/Host$$Lambda$R0', 'demo/Host$$Lambda$R1', 'demo/Host$$Lambda$R2',
                                     'demo/Host$$Lambda$R4', 'demo/Host$$Lambda$R5', 'demo/Host$$Lambda$R6',
                                     'demo/Host$$Lambda$R9', 'demo/Host$Inner$$Lambda$R0', 'demo/Many$$Lambda$R0',
                                     'demo/Many$$Lambda$R38']
    }

    def "a generated class carries the host's source file and synthetic methods"() {
        given:
        def loader = new URLClassLoader([output, runtime, optional].collect { it.toUri().toURL() } as URL[], (ClassLoader) null)
        Class<?> host = loader.loadClass('demo.Host')

        when:
        (host.getMethod('failing').invoke(null) as Supplier).get()

        then:
        IllegalStateException e = thrown()
        e.stackTrace[0].methodName.startsWith('lambda$failing$')
        e.stackTrace[1].className == 'demo.Host$$Lambda$R0'
        e.stackTrace[1].fileName == 'Host.java'

        and:
        def generated = loader.loadClass('demo.Host$$Lambda$R0')
        generated.declaredMethods.every { it.synthetic }
        generated.declaredConstructors.every { it.synthetic }
        !Modifier.isPublic(generated.modifiers)
        Modifier.isFinal(generated.modifiers)

        cleanup:
        loader?.close()
    }

    def "keeps the public API, serialVersionUID and every other class as compiled"() {
        given:
        def original = new URLClassLoader([classes, runtime, optional].collect { it.toUri().toURL() } as URL[], (ClassLoader) null)
        def desugared = new URLClassLoader([output, runtime, optional].collect { it.toUri().toURL() } as URL[], (ClassLoader) null)

        expect:
        ['demo.Host', 'demo.Host$Inner'].every { name ->
            api(original.loadClass(name)) == api(desugared.loadClass(name))
        }
        ObjectStreamClass.lookup(original.loadClass('demo.Host')).serialVersionUID ==
                ObjectStreamClass.lookup(desugared.loadClass('demo.Host')).serialVersionUID

        and: 'only the hosts change, and a host only at its rewritten sites and nest members'
        Files.readAllBytes(output.resolve('demo/Host.class')) != Files.readAllBytes(classes.resolve('demo/Host.class'))
        methodsWithoutCode(classes.resolve('demo/Host.class')) == methodsWithoutCode(output.resolve('demo/Host.class'))
        // serializable() and $deserializeLambda$ (altMetafactory), and compileOnly(), stay invokedynamic
        lambdaSites(output.resolve('demo/Host.class')) == 3
        lambdaSites(classes.resolve('demo/Host.class')) == 14

        cleanup:
        original?.close()
        desugared?.close()
    }

    def "native image initializes the generated classes at build time"() {
        when:
        String properties = output.resolve('META-INF/native-image/demo/demo-module/desugared-lambdas/native-image.properties').text
        Properties parsed = new Properties()
        parsed.load(new StringReader(properties))

        then:
        parsed.getProperty('Args').split(',')*.trim() == [
                '--initialize-at-build-time=demo.Host$$Lambda$R0',
                'demo.Host$$Lambda$R1', 'demo.Host$$Lambda$R2', 'demo.Host$$Lambda$R4', 'demo.Host$$Lambda$R5',
                'demo.Host$$Lambda$R6', 'demo.Host$$Lambda$R9', 'demo.Host$Inner$$Lambda$R0',
                'demo.Many$$Lambda$R0', 'demo.Many$$Lambda$R38']

        and: 'none without a name, nor without a generated class'
        !Files.exists(work.resolve('alone').resolve('META-INF'))
    }

    def "the output is deterministic"() {
        given:
        Path again = work.resolve('again')
        ModuleDesugaring.run(classes, [runtime], [optional, runtime], again, work.resolve('again.txt'), 'demo/demo-module')

        expect:
        files(again) == files(output)
        files(again).every { Files.readAllBytes(again.resolve(it)) == Files.readAllBytes(output.resolve(it)) }
        work.resolve('again.txt').text == report.text
    }

    def "without the runtime class path every site naming one of its types is kept"() {
        given:
        Path alone = work.resolve('alone')
        Path aloneReport = work.resolve('alone.txt')
        ModuleDesugaring.run(classes, [], [optional, runtime], alone, aloneReport, null)

        expect:
        aloneReport.text.contains('site\tdemo/Host\truntimeInterface()Ldep/Transformer;\t0\tkept\tunresolvedType\tdep/Transformer')
        aloneReport.readLines().contains('kept.unresolvedType.compileOnly=2')
    }

    def "selects projects by name, with or without the micronaut prefix"() {
        expect:
        LambdaDesugaring.selects(value, name) == expected

        where:
        value                   | name                | expected
        null                    | 'micronaut-core'    | false
        ''                      | 'micronaut-core'    | false
        'false'                 | 'micronaut-core'    | false
        'true'                  | 'micronaut-core'    | true
        'core, inject'          | 'micronaut-core'    | true
        'core,inject'           | 'micronaut-inject'  | true
        'micronaut-core'        | 'micronaut-core'    | true
        'core'                  | 'micronaut-core-processor' | false
        'core'                  | 'core'              | true
    }

    private Path compile(String name, Map<String, String> sources, List<Path> classpath) {
        Path src = work.resolve(name + '-src')
        Path out = work.resolve(name)
        Files.createDirectories(out)
        List<String> files = []
        sources.each { path, text ->
            Path file = src.resolve(path)
            Files.createDirectories(file.parent)
            file.text = text
            files << file.toString()
        }
        def args = ['-d', out.toString(), '-g', '--release', '25']
        if (classpath) {
            args += ['-cp', classpath.join(File.pathSeparator)]
        }
        int result = ToolProvider.systemJavaCompiler.run(null, null, null, (args + files) as String[])
        assert result == 0
        out
    }

    private static List<String> api(Class<?> type) {
        List<String> members = []
        type.declaredMethods.findAll { Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers) }
                .each { members << it.toGenericString() }
        type.declaredConstructors.findAll { Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers) }
                .each { members << it.toGenericString() }
        type.declaredFields.findAll { Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers) }
                .each { members << it.toGenericString() }
        members << type.toGenericString()
        members.sort()
    }

    private static List<String> methodsWithoutCode(Path file) {
        ClassModel model = ClassFile.of().parse(Files.readAllBytes(file))
        model.methods().collect { "${it.flags().flagsMask()} ${it.methodName().stringValue()}${it.methodType().stringValue()}".toString() }
    }

    private static int lambdaSites(Path file) {
        ClassModel model = ClassFile.of().parse(Files.readAllBytes(file))
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

    private static List<String> files(Path root) {
        List<String> names = []
        Files.walk(root).withCloseable { stream ->
            stream.filter { Files.isRegularFile(it) }.each { names << root.relativize(it).toString() }
        }
        names.sort()
    }
}
