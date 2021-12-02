package io.micronaut.docs;

public class RxJava implements JvmLibrary {

    private static final String PACKAGE_IO_REACTIVEX = "io.reactivex.";
    private static final String DEFAULT_URI = "http://reactivex.io/RxJava/2.x/javadoc";

    @Override
    public String getDefaultPackagePrefix() {
        return PACKAGE_IO_REACTIVEX;
    }

    public String defaultUri() {
        return DEFAULT_URI;
    }
}
