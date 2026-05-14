package io.micronaut.docs

import groovy.transform.CompileStatic

@CompileStatic
class Micronaut implements JvmLibrary {
    @Override
    String getDefaultPackagePrefix() {
        return "io.micronaut.";
    }

    @Override
    String defaultUri() {
        return "../api";
    }

}
