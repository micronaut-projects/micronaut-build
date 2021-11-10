package io.micronaut.docs

import groovy.transform.CompileStatic

@CompileStatic
class Jee implements JvmLibrary {

    private static final String DEFAULT_URI = "https://docs.oracle.com/javaee/6/api"

    @Override
    String defaultUri() {
        return DEFAULT_URI
    }
}
