package io.micronaut.docs;

public interface JvmLibrary {

    default String getDefaultPackagePrefix() {
        return null;
    }

    String defaultUri();
}
