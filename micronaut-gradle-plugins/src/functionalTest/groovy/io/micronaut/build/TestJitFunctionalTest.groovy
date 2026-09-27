package io.micronaut.build

class TestJitFunctionalTest extends AbstractFunctionalTest {

    private static final String PRINT_TEST_JVM_ARGS = """
        tasks.register("printTestJvmArgs") {
            def jvmArgs = providers.provider { tasks.test.allJvmArgs }
            doLast { println "Test JVM args: \${jvmArgs.get()}" }
        }
    """

    void "test JVMs use C2 as the top tier JIT"() {
        given:
        withSample("test-micronaut-module")
        file("subproject1/build.gradle") << PRINT_TEST_JVM_ARGS

        when:
        run 'printTestJvmArgs'

        then:
        outputContains '-XX:+UnlockExperimentalVMOptions, -XX:-UseJVMCICompiler'
    }

    void "a build can keep the Graal JIT for its test JVMs"() {
        given:
        withSample("test-micronaut-module")
        file("gradle.properties") << "\nmicronaut.test.graal-jit=true\n"
        file("subproject1/build.gradle") << PRINT_TEST_JVM_ARGS

        when:
        run 'printTestJvmArgs'

        then:
        outputContains 'Test JVM args:'
        outputDoesNotContain '-XX:-UseJVMCICompiler'
    }

    void "python test JVMs keep the Graal JIT when the python plugin is applied #order"() {
        given:
        withSample("test-micronaut-module")
        file("subproject1/build.gradle").text = buildScript + PRINT_TEST_JVM_ARGS

        when:
        run 'printTestJvmArgs'

        then:
        outputContains 'Test JVM args:'
        outputDoesNotContain '-XX:-UseJVMCICompiler'

        where:
        order    | buildScript
        'last'   | """
            plugins {
                id 'io.micronaut.build.internal.project-template-module'
                id 'io.micronaut.build.internal.python'
            }
        """
        'first'  | """
            plugins {
                id 'io.micronaut.build.internal.python'
                id 'io.micronaut.build.internal.project-template-module'
            }
        """
    }
}
