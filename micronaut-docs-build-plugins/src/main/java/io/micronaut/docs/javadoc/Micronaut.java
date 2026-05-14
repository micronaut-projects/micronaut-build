package io.micronaut.docs.javadoc;

public class Micronaut implements JvmLibrary {
    @Override
    public String getDefaultPackagePrefix() {
        return "io.micronaut.";
    }

    @Override
    public String defaultUri() {
        return "../api";
    }
}
