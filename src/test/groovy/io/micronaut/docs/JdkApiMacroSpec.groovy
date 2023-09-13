package io.micronaut.docs

class JdkApiMacroSpec extends AbstractConverterSpec {
    def "converts JDK link using default module"() {
        when:
        convert "jdk:java.util.concurrent.CompletableFuture[]"

        then:
        converted.contains '<a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html">CompletableFuture</a>'
    }

    def "can specify a module explicitly"() {
        when:
        convert "jdk:java.util.logging.ConsoleHandler[module=java.logging]"

        then:
        converted.contains '<a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.logging/java/util/logging/ConsoleHandler.html">ConsoleHandler</a>'
    }
}
