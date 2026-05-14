package io.micronaut.docs;

public class MicronautCore implements JvmLibrary {
    private static final String DEFAULT_URI = "https://docs.micronaut.io/latest/api";

    @Override
    public String defaultUri() {
        return DEFAULT_URI;
    }

    @Override
    public String getDefaultPackagePrefix() {
        return "io.micronaut";
    }
}
