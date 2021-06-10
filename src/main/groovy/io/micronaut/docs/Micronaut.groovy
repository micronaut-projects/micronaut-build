package io.micronaut.docs

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.NonNull
import javax.annotation.Nullable

@CompileStatic
class Micronaut implements JvmLibrary {
    @Nullable
    @Override
    String getDefaultPackagePrefix() {
        return "io.micronaut.";
    }

    @NonNull
    @Override
    String defaultUri() {
        return "../api";
    }

}
