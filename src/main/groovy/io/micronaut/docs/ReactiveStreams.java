package io.micronaut.docs;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

public class ReactiveStreams implements JvmLibrary {
    private static final String DEFAULT_URI = "https://www.reactive-streams.org/reactive-streams-1.0.3-javadoc";
    public static final String PACKAGE_ORG_REACTIVESTREAMS = "org.reactivestreams.";

    @Nullable
    @Override
    public String getDefaultPackagePrefix() {
        return PACKAGE_ORG_REACTIVESTREAMS;
    }

    @NonNull
    public String defaultUri() {
        return DEFAULT_URI;
    }
}
