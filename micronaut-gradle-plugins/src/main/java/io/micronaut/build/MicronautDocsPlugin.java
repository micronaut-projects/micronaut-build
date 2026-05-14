package io.micronaut.build;

import io.micronaut.build.docs.ConfigurationPropertiesPlugin;
import io.micronaut.build.docs.CreateReleasesDropdownTask;
import io.micronaut.build.docs.JavadocAggregatorPlugin;
import io.micronaut.build.docs.PrepareDocResourcesTask;
import io.micronaut.build.docs.PublishGuideTask;
import io.micronaut.build.docs.ValidateAsciidocOutputTask;
import io.micronaut.build.docs.props.MergeConfigurationReferenceTask;
import io.micronaut.build.docs.props.PublishConfigurationReferenceTask;
import io.micronaut.build.utils.GitHubApiService;
import io.micronaut.docs.macros.LanguageSnippetMacro;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.javadoc.Javadoc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
public abstract class MicronautDocsPlugin implements Plugin<Project> {

    static final String DOCUMENTATION_GROUP = "mndocs";
    public static final String CONFIGURATION_REFERENCE_HTML = "configurationreference.html";
    public static final String INDEX_HTML = "index.html";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        project.getPluginManager().apply(JavadocAggregatorPlugin.class);
        Object projectVersion = project.findProperty("projectVersion");
        Object projectDesc = project.findProperty("projectDesc");
        Object githubSlug = project.findProperty("githubSlug");
        project.getLogger().info("Configuring micronaut documentation tasks for subprojects.");
        project.getLogger().info("Add skipDocumentation=true to a submodule gradle.properties to skip docs");

        Configuration configProperties = project.getConfigurations().create("configProperties", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(false);
        });
        Configuration incomingIndividualConfigProps = project.getConfigurations().create("incomingIndividualConfigProps", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.extendsFrom(configProperties);
            configuration.attributes(attributes -> ConfigurationPropertiesPlugin.configureAttributes(
                attributes,
                project.getObjects(),
                ConfigurationPropertiesPlugin.INDIVIDUAL_CONFIGURATION_PROPERTIES
            ));
        });
        Configuration incomingConfigProps = project.getConfigurations().create("incomingConfigProps", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.extendsFrom(configProperties);
            configuration.attributes(attributes -> ConfigurationPropertiesPlugin.configureAttributes(
                attributes,
                project.getObjects(),
                ConfigurationPropertiesPlugin.CONFIGURATION_PROPERTIES
            ));
        });
        project.subprojects(subproject -> subproject.getPlugins().withType(ConfigurationPropertiesPlugin.class).configureEach(plugin -> {
            boolean skipDocs = subproject.hasProperty("skipDocumentation")
                && Boolean.parseBoolean(String.valueOf(subproject.property("skipDocumentation")));
            if (!skipDocs) {
                configProperties.getDependencies().add(project.getDependencies().create(subproject));
            }
        }));

        project.getConfigurations().maybeCreate("documentation");
        project.getDependencies().add("documentation", "org.fusesource.jansi:jansi:1.14");

        TaskProvider<Delete> cleanDocs = project.getTasks().register("cleanDocs", Delete.class, task -> {
            task.delete(project.getLayout().getBuildDirectory().dir("docs"));
        });

        project.getTasks().named("clean", Delete.class).configure(task -> task.dependsOn(cleanDocs));

        TaskProvider<PrepareDocResourcesTask> prepareDocsResources = project.getTasks().register("prepareDocsResources", PrepareDocResourcesTask.class, task -> {
            task.setGroup(DOCUMENTATION_GROUP);
            task.setDescription("Prepare resources for documentation");
            task.getResources().from(project.getLayout().getProjectDirectory().dir("src/main/docs/resources"));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("doc-resources"));
            task.getResourceClasspathJarName().set("grails-doc-files.jar");
        });

        Provider<Directory> processConfigPropsOutputDir = project.getLayout().getBuildDirectory().dir("working/01-includes/configurationProperties");

        TaskProvider<Copy> processConfigPropsTask = project.getTasks().register("processConfigProps", Copy.class, task -> {
            task.from(incomingIndividualConfigProps);
            task.into(processConfigPropsOutputDir);
        });

        List<String> langs = new ArrayList<>();
        langs.add("");
        langs.addAll(LanguageSnippetMacro.LANGS);
        Map<String, TaskProvider<PublishGuideTask>> publishGuideByLang = new LinkedHashMap<>();
        for (String lang : langs) {
            String taskName = "publishGuide" + capitalize(lang);
            publishGuideByLang.put(lang, project.getTasks().register(taskName, PublishGuideTask.class, task -> configureGuideTask(
                project,
                task,
                lang.isEmpty() ? null : lang,
                projectVersion,
                projectDesc,
                githubSlug,
                processConfigPropsTask,
                processConfigPropsOutputDir,
                prepareDocsResources
            )));
        }
        TaskProvider<PublishGuideTask> publishGuide = publishGuideByLang.get("");

        TaskProvider<MergeConfigurationReferenceTask> mergeConfigurationReference = project.getTasks().register(
            "mergeConfigurationReference",
            MergeConfigurationReferenceTask.class,
            task -> {
                task.getInputFiles().from(incomingConfigProps);
                task.getOutputFile().set(project.getLayout().getBuildDirectory().file("working/03-property-ref/adoc/propertyReference.adoc"));
                task.setGroup(DOCUMENTATION_GROUP);
            }
        );

        TaskProvider<PublishConfigurationReferenceTask> publishConfigurationReference = project.getTasks().register(
            "publishConfigurationReference",
            PublishConfigurationReferenceTask.class,
            task -> {
                task.getPropertyReferenceFile().set(mergeConfigurationReference.flatMap(MergeConfigurationReferenceTask::getOutputFile));
                task.getDestinationFile().set(project.getLayout().getBuildDirectory().file("working/03-property-ref/html/" + CONFIGURATION_REFERENCE_HTML));
                task.getVersion().set(String.valueOf(projectVersion));
                task.getPageTemplate().set(publishGuide.flatMap(guide -> guide.getResourcesDir().file("style/page.html")));
            }
        );

        // TODO: For now, we don't include language specific guides into the final assembly. Language specific guides
        //       are an experimental feature that needs more work before becoming the standard way docs are shipped.
        TaskProvider<Sync> assembleDocs = project.getTasks().register("assembleDocs", Sync.class, task -> {
            task.setDescription("Assembles the documentation");
            task.into(project.getLayout().getBuildDirectory().dir("working/04-assembled-docs"));
            task.from(publishGuide.flatMap(PublishGuideTask::getTargetDir), spec -> {
                spec.include(element -> {
                    String relativePath = element.getRelativePath().getPathString();
                    if (relativePath.startsWith("guide/")) {
                        return relativePath.equals("guide/index.html");
                    }
                    return true;
                });
            });
            task.from(publishConfigurationReference, spec -> spec.into("guide"));
            task.from(project.getTasks().withType(Javadoc.class), spec -> spec.into("api"));
        });

        TaskProvider<ValidateAsciidocOutputTask> validateAssembledDocs = project.getTasks().register(
            "validateAssembleDocs",
            ValidateAsciidocOutputTask.class,
            task -> {
                task.getInputDirectory().set(project.getLayout().getBuildDirectory().dir("working/04-assembled-docs"));
                task.getReport().set(project.getLayout().getBuildDirectory().file("working/reports/assemble-docs.txt"));
            }
        );

        assembleDocs.configure(task -> task.finalizedBy(validateAssembledDocs));

        TaskProvider<Zip> zipDocs = project.getTasks().register("zipDocs", Zip.class, task -> {
            task.setGroup(DOCUMENTATION_GROUP);
            task.getArchiveAppendix().set("docs");
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("distributions"));
            task.from(assembleDocs);
        });

        Provider<GitHubApiService> githubApi = GitHubApiService.registerOn(project);

        Provider<Boolean> createReleaseDropdownFlag = project.getProviders().gradleProperty("createReleaseDropdown")
            .map(Boolean::parseBoolean)
            .orElse(false);

        TaskProvider<CreateReleasesDropdownTask> createReleasesDropdown = project.getTasks().register(
            "createReleasesDropdown",
            CreateReleasesDropdownTask.class,
            task -> {
                task.setGroup(DOCUMENTATION_GROUP);
                task.usesService(githubApi);
                task.getSlug().set(String.valueOf(githubSlug));
                task.getVersion().set(String.valueOf(projectVersion));
                task.getSourceIndex().set(publishGuide.flatMap(guide -> guide.getTargetDir().file("guide/index.html")));
                task.getOutputIndex().set(project.getLayout().getBuildDirectory().file("working/05-dropdown/index.html"));
                if (createReleaseDropdownFlag.get()) {
                    task.getVersionsJson().set(githubApi.zip(task.getSlug(), (api, ghSlug) -> {
                        try {
                            byte[] jsonArr = api.fetchTagsFromGitHub(ghSlug);
                            return new String(jsonArr, StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            task.getLogger().error("Exception fetching github tags for " + ghSlug, e);
                            return "[]";
                        }
                    }));
                }
                task.getOutputs().doNotCacheIf("error while fetching releases list", candidate ->
                    ((CreateReleasesDropdownTask) candidate).getVersionsJson().getOrElse("[]").equals("[]")
                );
            }
        );

        TaskProvider<Copy> assembleFinalDocs = project.getTasks().register("assembleFinalDocs", Copy.class, task -> {
            task.into(project.getLayout().getBuildDirectory().dir("docs"));
            task.from(assembleDocs, spec -> spec.exclude("guide/index.html"));
            task.into("guide", spec -> spec.from(createReleasesDropdown));
        });

        project.getTasks().register("docs", task -> task.dependsOn(assembleDocs, assembleFinalDocs, zipDocs));
    }

    private static void configureGuideTask(Project project,
                                           PublishGuideTask task,
                                           String lang,
                                           Object projectVersion,
                                           Object projectDesc,
                                           Object githubSlug,
                                           TaskProvider<Copy> processConfigPropsTask,
                                           Provider<Directory> processConfigPropsOutputDir,
                                           TaskProvider<PrepareDocResourcesTask> prepareDocsResources) {
        task.setGroup(DOCUMENTATION_GROUP);
        task.setDescription(lang == null ? "Generate Guide" : "Generate Guide (" + lang + ")");
        Object kafkaVersion = project.getRootProject().hasProperty("kafkaVersion")
            ? project.getRootProject().property("kafkaVersion")
            : "N/A";
        task.getInputs().files(processConfigPropsTask.map(Copy::getDestinationDir));
        task.getInputs().property("Project description", projectDesc);
        task.getInputs().property("Kafka version", kafkaVersion);
        // DocPublisher adds the language as a subdir if set.
        if (lang != null) {
            task.getLanguage().set(lang);
            task.getTargetDir().set(project.getLayout().getBuildDirectory().dir("working/02-docs-raw"));
        } else {
            task.getTargetDir().set(project.getLayout().getBuildDirectory().dir("working/02-docs-raw/all"));
        }
        String githubBranch = currentGitBranch(project);
        task.getSourceRepo().set("https://github.com/" + githubSlug + "/edit/" + githubBranch + "/src/main/docs");
        task.getSourceDir().set(project.getLayout().getProjectDirectory().dir("src/main/docs"));
        task.getResourcesDir().set(prepareDocsResources.flatMap(PrepareDocResourcesTask::getOutputDirectory));
        task.getPropertiesFiles().from(project.getRootProject().file("gradle.properties"));
        task.getAsciidoc().set(true);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("safe", "UNSAFE");
        properties.put("source-highlighter", "highlightjs");
        properties.put("version", projectVersion);
        properties.put("subtitle", projectDesc);
        properties.put("github", "https://github.com/micronaut-projects/micronaut-core");
        properties.put("api", "../api");
        properties.put("micronautapi", "https://docs.micronaut.io/latest/api");
        properties.put("sourceDir", project.getRootProject().getProjectDir().getAbsolutePath());
        properties.put("sourcedir", project.getRootProject().getProjectDir().getAbsolutePath());
        properties.put("includedir", processConfigPropsOutputDir.get().getAsFile().getParentFile() + "/");
        properties.put("javaee", "https://docs.oracle.com/en/java/javase/21/docs/api/");
        properties.put("javase", "https://jakarta.ee/specifications/platform/9/apidocs");
        properties.put("groovyapi", "http://docs.groovy-lang.org/latest/html/gapi/");
        properties.put("grailsapi", "http://docs.grails.org/latest/api/");
        properties.put("gormapi", "http://gorm.grails.org/latest/api/");
        properties.put("springapi", "https://docs.spring.io/spring/docs/current/javadoc-api/");
        properties.put("kafka-version", kafkaVersion);
        properties.put("default-language", lang == null ? "" : lang);
        task.getProperties().putAll(properties);
    }

    private static String currentGitBranch(Project project) {
        Process process = null;
        try {
            process = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(project.getRootProject().getProjectDir())
                .redirectErrorStream(true)
                .start();
            String text;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                text = reader.lines().collect(Collectors.joining("\n")).trim();
            }
            if (process.waitFor() == 0 && !text.isEmpty()) {
                return text;
            }
        } catch (IOException e) {
            project.getLogger().debug("Unable to determine git branch", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            project.getLogger().debug("Interrupted while determining git branch", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return "master";
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }
}
