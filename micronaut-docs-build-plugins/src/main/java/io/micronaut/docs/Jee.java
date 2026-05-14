package io.micronaut.docs;

public class Jee implements JvmLibrary {
    private static final String DEFAULT_URI = "https://docs.oracle.com/javaee/6/api";

    @Override
    public String defaultUri() {
        return DEFAULT_URI;
    }
}
