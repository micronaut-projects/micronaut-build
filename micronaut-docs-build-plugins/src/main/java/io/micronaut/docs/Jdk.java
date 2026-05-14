package io.micronaut.docs;

public class Jdk implements JvmLibrary {
    private static final String DEFAULT_URI = "https://docs.oracle.com/en/java/javase/21/docs/api";

    @Override
    public String defaultUri() {
        return DEFAULT_URI;
    }
}
