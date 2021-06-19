package io.micronaut.docs;

import io.micronaut.core.annotation.NonNull;
import org.jetbrains.annotations.Nullable;

public class Reactor implements JvmLibrary {
    private static final String DEFAULT_URI = "https://projectreactor.io/docs/core/release/api";
    private static final String PACKAGE_REACTOR = "reactor.core.publisher.";

    @Nullable
    @Override
    public String getDefaultPackagePrefix() {
        return PACKAGE_REACTOR;
    }

    @NonNull
    @Override
    public String defaultUri() {
        return DEFAULT_URI;
    }
}
