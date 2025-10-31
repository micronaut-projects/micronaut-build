package io.micronaut.docs;

public class Reactor implements JvmLibrary {
    private static final String DEFAULT_URI = "https://projectreactor.io/docs/core/release/api";
    private static final String PACKAGE_REACTOR = "reactor.core.publisher.";

    @Override
    public String getDefaultPackagePrefix() {
        return PACKAGE_REACTOR;
    }

    @Override
    public String defaultUri() {
        return DEFAULT_URI;
    }
}
