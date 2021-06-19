package io.micronaut.docs

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.NonNull

@CompileStatic
class Jee implements JvmLibrary {

    private static final String DEFAULT_URI = "https://docs.oracle.com/javaee/6/api"

    @NonNull
    @Override
    String defaultUri() {
        return DEFAULT_URI
    }
}
