package io.micronaut.docs

interface JvmLibrary {

    default String getDefaultPackagePrefix() {
        null
    }

    String defaultUri()
}
