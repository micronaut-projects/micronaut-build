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
package io.micronaut.build.docs.props;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@CacheableTask
public abstract class PublishConfigurationReferenceTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPropertyReferenceFile();

    @Input
    public abstract Property<String> getVersion();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPageTemplate();

    @Inject
    public abstract ProviderFactory getProviders();

    @OutputFile
    public abstract RegularFileProperty getDestinationFile();

    @TaskAction
    void publishConfigurationReference() {
        File referenceFile = getPropertyReferenceFile().getAsFile().get();
        if (referenceFile.exists()) {
            String textPage = getProviders().fileContents(getPageTemplate()).getAsText().get();
            textPage = textPage.replace("@projectVersion@", getVersion().get());
            textPage = textPage.replace("@pagetitle@", "Configuration Reference | Micronaut");
            try {
                String html = Asciidoctor.Factory.create()
                        .convert(getProviders().fileContents(getPropertyReferenceFile()).getAsText().get(), Options.builder().build());
                textPage = textPage.replace("@docscontent@", html);
                try (FileOutputStream fos = new FileOutputStream(getDestinationFile().getAsFile().get())) {
                    fos.write(textPage.getBytes(StandardCharsets.UTF_8.name()));
                }
            } catch(Exception e) {
                getLogger().error("Error raised rendering asciidoc file {}" , getDestinationFile().getAsFile().get());
            }

        } else {
            getLogger().quiet("{} does not exist.", referenceFile);
        }
    }}
