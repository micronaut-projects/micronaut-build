/*
 * Copyright 2003-2021 the original author or authors.
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
package io.micronaut.build.compat;

import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser;
import io.micronaut.build.catalogs.internal.Library;
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel;
import io.micronaut.build.catalogs.internal.VersionModel;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This task is responsible for checking the compatibility of version catalogs
 * over time.
 */
@CacheableTask
public abstract class VersionCatalogCompatibilityCheck extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBaseline();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getCurrent();

    @Input
    public abstract SetProperty<String> getAcceptedVersionRegressions();

    @Input
    public abstract SetProperty<String> getAcceptedLibraryRegressions();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void checkCompatibility() throws IOException {
        VersionCatalogTomlModel baselineModel = parse(getBaseline());
        VersionCatalogTomlModel currentModel = parse(getCurrent());
        Set<String> removedVersions = diff(collectVersionsFrom(baselineModel), collectVersionsFrom(currentModel), getAcceptedVersionRegressions().get());
        Set<String> removedLibs = diff(collectLibrariesFrom(baselineModel), collectLibrariesFrom(currentModel), getAcceptedLibraryRegressions().get());
        List<String> report = new ArrayList<>();
        boolean fail = false;
        if (!removedVersions.isEmpty()) {
            fail = true;
            report.add("The following versions were present in the baseline version but missing from this catalog:");
            removedVersions.forEach(version -> report.add("  - " + version));
        }
        if (!removedLibs.isEmpty()) {
            fail = true;
            report.add("The following libraries were present in the baseline version but missing from this catalog:");
            removedLibs.forEach(version -> report.add("  - " + version));
        }
        Files.write(getReportFile().getAsFile().get().toPath(), report, StandardCharsets.UTF_8);
        if (fail) {
            throw new RuntimeException("Version catalogs are not compatible:\n" + String.join("\n", report));
        }
    }

    private static Set<String> collectVersionsFrom(VersionCatalogTomlModel model) {
        return model.getVersionsTable()
                .stream()
                .map(VersionModel::getReference)
                .collect(Collectors.toSet());
    }

    private static Set<String> collectLibrariesFrom(VersionCatalogTomlModel model) {
        return model.getLibrariesTable()
                .stream()
                .map(Library::getAlias)
                .collect(Collectors.toSet());
    }

    private VersionCatalogTomlModel parse(RegularFileProperty file) {
        LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
        try (InputStream in = Files.newInputStream( file.getAsFile().get().toPath())) {
            parser.parse(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return parser.getModel();
    }

    private static Set<String> diff(Set<String> baseline, Set<String> current, Set<String> acceptedRegressions) {
        Set<String> removedVersions = new HashSet<>(baseline);
        removedVersions.removeAll(current);
        removedVersions.removeAll(acceptedRegressions);
        return removedVersions;
    }
}
