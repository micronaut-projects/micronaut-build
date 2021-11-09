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

import io.micronaut.build.docs.props.JavaDocAtValueReplacementTask;
import io.micronaut.build.docs.props.ProcessConfigPropsTask;
import io.micronaut.build.docs.props.ReplaceAtLinkTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * This plugin is applied on Micronaut modules and is responsible
 * for post-processing the generated configuration-properties.adoc
 * files.
 *
 * It will expose the result as a configuration (configurationPropertiesElements)
 * which can be used by consumers to get the processed files.
 *
 */
public abstract class ConfigurationPropertiesPlugin implements Plugin<Project> {
    private static final String DOCUMENTATION_GROUP = "mndocs";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
            TaskContainer tasks = project.getTasks();
            DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
            Provider<Directory> genDir = buildDirectory.dir("config-properties");
            Configuration configurationPropertiesElements = createConfigurationPropertiesOutgoingConfiguration(project);
            SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            TaskProvider<JavaDocAtValueReplacementTask> javaDocAtReplacement = tasks.register("javaDocAtReplacement", JavaDocAtValueReplacementTask.class, task -> {
                task.setGroup(DOCUMENTATION_GROUP);
                task.getSourceFiles().from(mainSourceSet.getAllJava());
                task.getInputFiles().from(mainSourceSet.getOutput().getAsFileTree().filter(f -> "config-properties.adoc".equals(f.getName())));
                task.getOutputFile().convention(genDir.map(dir -> dir.file("config-properties-javadocat-replaced.adoc")));
            });

            TaskProvider<ReplaceAtLinkTask> replaceAtLink = tasks.register("replaceAtLink", ReplaceAtLinkTask.class, task -> {
                task.setGroup(DOCUMENTATION_GROUP);
                task.getInputFiles().from(javaDocAtReplacement);
                task.getOutputFile().convention(genDir.map(dir -> dir.file("config-properties-atlink-replaced.adoc")));
            });

            // This is the final task of the chain, which generates what is consumed by the top-level project
            TaskProvider<ProcessConfigPropsTask> processConfigurationProperties = tasks.register("processConfigProps", ProcessConfigPropsTask.class, task -> {
                task.setGroup(DOCUMENTATION_GROUP);
                task.getInputFiles().from(replaceAtLink);
                task.getOutputDirectory().convention(genDir.map(d -> d.dir("processed")));
            });
            configurationPropertiesElements.getOutgoing().artifact(processConfigurationProperties);
        });
    }

    /**
     * Creates a configuration which will carry the project's generated configuration properties.
     * @param project the project
     * @return the outgoing configuration
     */
    private Configuration createConfigurationPropertiesOutgoingConfiguration(Project project) {
        return project.getConfigurations().create("configurationPropertiesElements", conf -> {
            conf.setDescription("Configuration properties adoc files");
            conf.setCanBeConsumed(true);
            conf.setCanBeResolved(false);
            conf.attributes(attrs -> configureAttributes(attrs, project.getObjects()));
        });
    }

    public static void configureAttributes(AttributeContainer attrs, ObjectFactory objects) {
        attrs.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
        attrs.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, "configuration-properties"));
    }

}
