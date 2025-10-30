package io.micronaut.build.aot

import io.micronaut.build.AbstractFunctionalTest

class AOTModuleFunctionalTest extends AbstractFunctionalTest {
    def "can create an AOT module"() {
        given:
        withSample("test-aot-module")

        when:
        run 'test'

        then:
        tasks {
            succeeded ':compileJava'
            succeeded ':compileTestGroovy'
            succeeded ':test'
        }

    }
}
