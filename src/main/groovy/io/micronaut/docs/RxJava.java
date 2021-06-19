package io.micronaut.docs;

import io.micronaut.core.annotation.NonNull;

import javax.annotation.Nullable;

public class RxJava implements JvmLibrary {

    private static final String PACKAGE_IO_REACTIVEX = "io.reactivex.";
    private static final String DEFAULT_URI = "http://reactivex.io/RxJava/2.x/javadoc";

    @Override
    @Nullable
    public String getDefaultPackagePrefix() {
        return PACKAGE_IO_REACTIVEX;
    }

    @NonNull
    public String defaultUri() {
        return DEFAULT_URI;
    }
}
