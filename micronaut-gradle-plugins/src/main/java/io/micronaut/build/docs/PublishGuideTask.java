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
package io.micronaut.build.docs;

import io.micronaut.docs.GuidePublisher;
import io.micronaut.docs.GuidePublisherFactory;
import io.micronaut.docs.SnippetSourceResolver;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

@CacheableTask
public abstract class PublishGuideTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDir();

    @OutputDirectory
    public abstract DirectoryProperty getTargetDir();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract DirectoryProperty getResourcesDir();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getPropertiesFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getSnippetSourceFiles();

    @Input
    @Optional
    public abstract Property<String> getLanguage();

    @Input
    public abstract Property<Boolean> getAsciidoc();

    @Input
    @Optional
    public abstract Property<String> getSourceRepo();

    @Input
    public abstract MapProperty<String, Object> getProperties();

    @Inject
    protected abstract FileOperations getFileOperations();

    @Inject
    public PublishGuideTask() {
        ProjectLayout layout = getProject().getLayout();
        Directory projectDirectory = layout.getProjectDirectory();
        DirectoryProperty buildDirectory = layout.getBuildDirectory();
        getSourceDir().convention(projectDirectory.dir("src"));
        getTargetDir().convention(buildDirectory.dir("docs"));
        getResourcesDir().convention(projectDirectory.dir("resources"));
        getAsciidoc().convention(false);
        getSnippetSourceFiles().from(getProject().provider(this::resolveSnippetSourceFiles));
    }

    private static void loadProperties(Properties into, File src) throws IOException {
        try (FileInputStream fis = new FileInputStream(src)) {
            into.load(fis);
        }
    }

    private Set<File> resolveSnippetSourceFiles() {
        return SnippetSourceResolver.findSnippetSourceFiles(
            getSourceDir().get().getAsFile(),
            getProject().getProjectDir(),
            getLanguage().getOrElse("")
        );
    }

    private static GuidePublisher newPublisher(File sourceDir, File targetDir) {
        return ServiceLoader.load(GuidePublisherFactory.class, GuidePublisherFactory.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No " + GuidePublisherFactory.class.getName() + " provider found."))
            .create(sourceDir, targetDir);
    }

    @TaskAction
    public void generate() throws IOException {
        Properties props = new Properties();
        File docProperties = getResourcesDir().file("doc.properties").get().getAsFile();
        if (docProperties.exists()) {
            loadProperties(props, docProperties);
        }

        for (File f : getPropertiesFiles().getFiles()) {
            loadProperties(props, f);
        }

        props.putAll(getProperties().get());

        File sourceDir = getSourceDir().getAsFile().get();
        File targetDir = getTargetDir().getAsFile().get();
        GuidePublisher publisher = newPublisher(sourceDir, targetDir);
        publisher.setFileOperations(getFileOperations());
        publisher.setAsciidoc(getAsciidoc().get());
        publisher.setDocResources(getResourcesDir().getAsFile().get());
        publisher.setApiDir(targetDir);
        publisher.setLanguage(getLanguage().getOrElse(""));
        publisher.setSourceRepo(getSourceRepo().getOrNull());
        publisher.setImages(getResourcesDir().dir("img").get().getAsFile());
        publisher.setCss(getResourcesDir().dir("css").get().getAsFile());
        publisher.setFonts(getResourcesDir().dir("fonts").get().getAsFile());
        publisher.setJs(getResourcesDir().dir("js").get().getAsFile());
        publisher.setStyle(getResourcesDir().dir("style").get().getAsFile());
        publisher.setVersion(String.valueOf(props.get("grails.version")));

        // Aliases and other doc.properties entries are passed in as engine properties. This
        // is how the doc title, subtitle, etc. are set.
        publisher.setEngineProperties(props);

        // Add custom macros.

        // {hidden} macro for enabling translations.
        publisher.registerHiddenMacro();

        // Radeox loads its bundles off the context class loader, which
        // unfortunately doesn't contain the grails-docs JAR. So, we
        // temporarily switch the DocPublisher class loader into the
        // thread so that the Radeox bundles can be found.
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(publisher.getClass().getClassLoader());
            publisher.publish();
        } finally {
            // Restore the old context class loader.
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }


}
