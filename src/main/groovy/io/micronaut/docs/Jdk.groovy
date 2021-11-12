package io.micronaut.docs

import groovy.transform.CompileStatic

@CompileStatic
class Jdk implements JvmLibrary {
    private static final String DEFAULT_URI = "https://docs.oracle.com/javase/8/docs/api"

    @Override
    String defaultUri() {
        return DEFAULT_URI
    }
}
