/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.catalogs.internal;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Reads module coordinates from a catalog published alongside a BOM.
 * Gradle resolves the input file using the configured repositories and cache.
 */
public abstract class BomCatalogCoordinatesSource implements ValueSource<String, BomCatalogCoordinatesSource.Parameters> {
    public interface Parameters extends ValueSourceParameters {
        ConfigurableFileCollection getCatalog();
        Property<String> getGroup();
        Property<String> getArtifact();
    }

    @Override
    @Nullable
    public String obtain() {
        var parameters = getParameters();
        var parser = new LenientVersionCatalogParser();
        try (var stream = Files.newInputStream(parameters.getCatalog().getSingleFile().toPath())) {
            parser.parse(stream);
        } catch (IOException e) {
            throw new GradleException("Cannot read the Micronaut BOM catalog", e);
        }
        var model = parser.getModel();
        var module = parameters.getGroup().get() + ":" + parameters.getArtifact().get();
        var library = model.findLibrary(parameters.getGroup().get(), parameters.getArtifact().get()).orElse(null);
        if (library == null) {
            return null;
        }
        var version = library.getVersion();
        var rich = version.getReference() == null ? version.getVersion() :
            model.findVersion(version.getReference()).map(VersionModel::getVersion).orElse(null);
        if (rich == null || rich.getRequire() == null || rich.getRequire().isBlank()) {
            return null;
        }
        return module + ":" + rich.getRequire();
    }
}
