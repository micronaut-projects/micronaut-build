package io.micronaut.docs

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.NonNull

@CompileStatic
class Jdk implements JvmLibrary {
    private static final String DEFAULT_URI = "https://docs.oracle.com/javase/8/docs/api"

    @NonNull
    @Override
    String defaultUri() {
        return DEFAULT_URI
    }
}
