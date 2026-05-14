package io.micronaut.docs;

import io.micronaut.docs.javadoc.Jdk;
import io.micronaut.docs.javadoc.Jee;
import io.micronaut.docs.javadoc.JvmLibrary;
import io.micronaut.docs.javadoc.Micronaut;
import io.micronaut.docs.javadoc.MicronautCore;
import io.micronaut.docs.javadoc.ReactiveStreams;
import io.micronaut.docs.javadoc.Reactor;
import io.micronaut.docs.javadoc.RxJava;
import io.micronaut.docs.macros.ApiMacro;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JvmLibraryTest {

    @Test
    void exposesJdkLibraryDefaults() {
        var library = new Jdk();

        assertNoPackagePrefix(library, "https://docs.oracle.com/en/java/javase/21/docs/api");
        assertEquals("java/util/List", ApiMacro.targetPathUrl("java.util.List", library));
    }

    @Test
    void exposesJeeLibraryDefaults() {
        var library = new Jee();

        assertNoPackagePrefix(library, "https://docs.oracle.com/javaee/6/api");
        assertEquals("javax/annotation/PostConstruct", ApiMacro.targetPathUrl("javax.annotation.PostConstruct", library));
    }

    @Test
    void exposesMicronautLibraryDefaults() {
        var library = new Micronaut();

        assertPackagePrefix(library, "../api", "io.micronaut.");
        assertEquals("io/micronaut/http/HttpRequest", ApiMacro.targetPathUrl("http.HttpRequest", library));
    }

    @Test
    void exposesMicronautCoreLibraryDefaults() {
        var library = new MicronautCore();

        assertPackagePrefix(library, "https://docs.micronaut.io/latest/api", "io.micronaut");
        assertEquals("io/micronaut/context/ApplicationContext", ApiMacro.targetPathUrl("io.micronaut.context.ApplicationContext", library));
    }

    @Test
    void exposesReactiveStreamsLibraryDefaults() {
        var library = new ReactiveStreams();

        assertPackagePrefix(library, "https://www.reactive-streams.org/reactive-streams-1.0.3-javadoc", "org.reactivestreams.");
        assertEquals("org/reactivestreams/Publisher", ApiMacro.targetPathUrl("Publisher", library));
    }

    @Test
    void exposesReactorLibraryDefaults() {
        var library = new Reactor();

        assertPackagePrefix(library, "https://projectreactor.io/docs/core/release/api", "reactor.core.publisher.");
        assertEquals("reactor/core/publisher/Flux", ApiMacro.targetPathUrl("Flux", library));
    }

    @Test
    void exposesRxJavaLibraryDefaults() {
        var library = new RxJava();

        assertPackagePrefix(library, "http://reactivex.io/RxJava/2.x/javadoc", "io.reactivex.");
        assertEquals("io/reactivex/Flowable", ApiMacro.targetPathUrl("Flowable", library));
    }

    private static void assertNoPackagePrefix(JvmLibrary library, String defaultUri) {
        assertEquals(defaultUri, library.defaultUri());
        assertNull(library.getDefaultPackagePrefix());
    }

    private static void assertPackagePrefix(JvmLibrary library, String defaultUri, String packagePrefix) {
        assertEquals(defaultUri, library.defaultUri());
        assertEquals(packagePrefix, library.getDefaultPackagePrefix());
    }
}
