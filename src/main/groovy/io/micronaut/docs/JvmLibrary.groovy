package io.micronaut.docs

import io.micronaut.core.annotation.NonNull
import javax.annotation.Nullable

interface JvmLibrary {

    @Nullable
    default String getDefaultPackagePrefix() {
        null
    }

    @NonNull
    String defaultUri()
}
