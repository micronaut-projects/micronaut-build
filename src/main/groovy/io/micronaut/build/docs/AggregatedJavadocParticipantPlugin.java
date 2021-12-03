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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.jetbrains.annotations.NotNull;

/**
 * A plugin which marks a project as a participant
 * in the aggregated javadoc generation. The aggregator
 * will need information about the javadoc classpath,
 * includes and excludes.
 */
public class AggregatedJavadocParticipantPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            Configuration internalJavadocElements = createFilteredJavadocSourcesElements(project);
            Configuration javadocElementClasspath = createJavadocElementClasspath(project);
            TaskProvider<PrepareJavadocAggregationTask> prepareTask = project.getTasks().register("prepareJavadocAggregation", PrepareJavadocAggregationTask.class, task -> {
                Javadoc javadoc = project.getTasks().named("javadoc", Javadoc.class).get();
                JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
                SourceSet sourceSet = javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                task.getSources().from(sourceSet.getAllJava().getSourceDirectories());
                task.getIncludes().set(javadoc.getIncludes());
                task.getExcludes().set(javadoc.getExcludes());
                task.getOutputDir().set(project.getLayout().getBuildDirectory().dir("aggregation/javadoc"));
            });
            internalJavadocElements.getOutgoing().artifact(prepareTask);
        });
    }

    private Configuration createJavadocElementClasspath(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        return configurations.create("internalJavadocClasspathElements", conf -> {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(true);
            Configuration compileClasspath = configurations.getByName("compileClasspath");
            AttributeContainer compileClasspathAttrs = compileClasspath.getAttributes();
            conf.extendsFrom(compileClasspath);
            conf.attributes(attrs -> compileClasspathAttrs.keySet().forEach(key -> {
                Object attribute = compileClasspathAttrs.getAttribute(key);
                //noinspection unchecked
                attrs.attribute((Attribute<Object>)key, attribute);
                attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, JavadocAggregationUtils.AGGREGATED_JAVADOC_PARTICIPANT_DEPS));
            }));
        });
    }

    @NotNull
    private Configuration createFilteredJavadocSourcesElements(Project project) {
        return project.getConfigurations().create("internalJavadocElements", conf -> {
            conf.setCanBeConsumed(true);
            conf.setCanBeResolved(false);
            ObjectFactory objects = project.getObjects();
            JavadocAggregationUtils.configureJavadocSourcesAggregationAttributes(objects, conf);
        });
    }
}
