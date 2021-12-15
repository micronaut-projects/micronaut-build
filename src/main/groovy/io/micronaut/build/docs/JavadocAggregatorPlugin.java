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

import io.micronaut.build.MicronautBuildExtension;
import io.micronaut.build.MicronautBuildExtensionPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CacheableTask
public abstract class JavadocAggregatorPlugin implements Plugin<Project> {
    private final static Pattern MICRONAUT_DOCS_URI_PATTERN = Pattern.compile("^(https?://micronaut-projects\\.github\\.io/micronaut-(?:[a-z-A-Z0-9]-?)+/latest/api/)(.*)$");

    @Override
    public void apply(Project project) {
        // todo: replace with a narrower plugin once
        //  https://github.com/gradle/gradle/issues/19234 is fixed.
        project.getPluginManager().apply(JavaBasePlugin.class);
        project.getPluginManager().apply(MicronautBuildExtensionPlugin.class);
        MicronautBuildExtension micronautBuildExtension = project.getExtensions().getByType(MicronautBuildExtension.class);
        project.getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE).getCompatibilityRules().add(ApiCompatibilityRule.class);
        Configuration javadocAggregatorBase = createAggregationConfigurationBase(project);
        Configuration javadocAggregator = createAggregationConfiguration(project, javadocAggregatorBase);
        Configuration javadocAggregatorClasspath = createAggregationConfigurationClasspath(project, javadocAggregatorBase);
        project.getSubprojects().forEach(subproject -> {
            subproject.getPlugins().withType(AggregatedJavadocParticipantPlugin.class, plugin -> {
                project.evaluationDependsOn(subproject.getPath());
                javadocAggregatorBase.getDependencies().add(
                        project.getDependencies().create(subproject)
                );
            });
        });
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        project.getTasks().register("javadoc", Javadoc.class, javadoc -> {
            javadoc.setDescription("Generate javadocs from all child projects as if it was a single project");
            javadoc.setGroup("Documentation");

            javadoc.setDestinationDir(project.getLayout().getBuildDirectory().dir("working/00-api").get().getAsFile());
            javadoc.setTitle(project.getName() + " " + project.getVersion() + " API");
            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
            options.author(true);
            javadoc.exclude("example/**");
            // Use Java Toolchain to render consistent javadocs
            javadoc.getJavadocTool().convention(toolchains.javadocToolFor(spec -> spec.getLanguageVersion().set(asLanguageVersion(micronautBuildExtension))));
            options.setSource(micronautBuildExtension.getSourceCompatibility());
            options.links(project.getProperties()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().endsWith("api"))
                    .map(Map.Entry::getValue)
                    .map(String::valueOf)
                    .filter(link -> link.startsWith("http"))
                    .map(link -> {
                        Matcher m = MICRONAUT_DOCS_URI_PATTERN.matcher(link);
                        if (m.matches() && !m.group(2).isEmpty()) {
                            project.getLogger().warn("[javadocs] gradle.properties file declares entry {} with should stop at /api. Automatically truncated to {}", link, m.group(1));
                            return m.group(1);
                        }
                        return link;
                    })
                    .toArray(String[]::new));
            options.addStringOption("Xdoclint:none", "-quiet");
            options.addBooleanOption("notimestamp", true);
            javadoc.setSource(javadocAggregator);
            javadoc.setClasspath(javadocAggregatorClasspath);
        });
    }

    private static JavaLanguageVersion asLanguageVersion(MicronautBuildExtension micronautBuildExtension) {
        String sourceCompatibility = micronautBuildExtension.getSourceCompatibility();
        if (sourceCompatibility.startsWith("1.")) {
            sourceCompatibility = sourceCompatibility.substring(2);
        }
        return JavaLanguageVersion.of(sourceCompatibility);
    }

    private Configuration createAggregationConfiguration(Project project, Configuration javadocAggregatorBase) {
        return project.getConfigurations().create("javadocAggregator", conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.extendsFrom(javadocAggregatorBase);
            JavadocAggregationUtils.configureJavadocSourcesAggregationAttributes(project.getObjects(), conf);
        });
    }

    private Configuration createAggregationConfigurationClasspath(Project project, Configuration javadocAggregatorBase) {
        return project.getConfigurations().create("javadocAggregatorClasspath", conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.extendsFrom(javadocAggregatorBase);
            conf.attributes(attrs -> {
                attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, JavadocAggregationUtils.AGGREGATED_JAVADOC_PARTICIPANT_DEPS));
                attrs.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attrs.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            });
        });
    }

    private Configuration createAggregationConfigurationBase(Project project) {
        return project.getConfigurations().create("javadocAggregatorBase", conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(false);
        });
    }

    public static class ApiCompatibilityRule implements AttributeCompatibilityRule<Usage> {
        @Override
        public void execute(CompatibilityCheckDetails<Usage> details) {
            Usage consumerValue = details.getConsumerValue();
            if (consumerValue != null && consumerValue.getName().equals(JavadocAggregationUtils.AGGREGATED_JAVADOC_PARTICIPANT_DEPS)) {
                Usage producerValue = details.getProducerValue();
                if (producerValue != null && producerValue.getName().equals(Usage.JAVA_API)) {
                    details.compatible();
                }
            }
        }
    }
}
