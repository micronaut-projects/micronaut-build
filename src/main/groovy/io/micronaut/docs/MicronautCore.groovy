package io.micronaut.docs

import groovy.transform.CompileStatic

@CompileStatic
class MicronautCore implements JvmLibrary {
    private static final String DEFAULT_URI = "https://docs.micronaut.io/latest/api"

    @Override
    String defaultUri() {
        return DEFAULT_URI
    }

    @Override
    String getDefaultPackagePrefix() {
        return "io.micronaut";
    }
}
