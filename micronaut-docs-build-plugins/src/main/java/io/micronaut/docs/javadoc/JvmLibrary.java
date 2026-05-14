package io.micronaut.docs.javadoc;

public interface JvmLibrary {

    default String getDefaultPackagePrefix() {
        return null;
    }

    String defaultUri();
}
