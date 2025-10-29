package io.micronaut.build;

import io.github.gradlenexus.publishplugin.InitializeNexusStagingRepository;
import io.micronaut.build.utils.ProviderUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.VariantVersionMappingStrategy;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.plugins.signing.Sign;
import org.gradle.plugins.signing.SigningExtension;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static io.micronaut.build.MicronautPlugin.PRE_RELEASE_CHECK_TASK_NAME;
import static io.micronaut.build.MicronautPlugin.moduleNameOf;

/**
 * Micronaut internal Gradle plugin. Not intended to be used in user's projects.
 */
public abstract class MicronautPublishingPlugin implements Plugin<Project> {
    private static final String[] EXTRA_JAR_TASKS = {"javadocJar", "sourcesJar"};
    private static final Set<String> JARS_TO_EMBED_POM = new HashSet<>(Arrays.asList("jar", "shadowJar"));

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        ProviderFactory providers = project.getProviders();
        TaskContainer tasks = project.getTasks();
        ExtensionContainer extensions = project.getExtensions();
        if (isPublishingDisabledFor(project)) {
            return;
        }
        plugins.apply(MicronautBuildExtensionPlugin.class);
        MicronautBuildExtension micronautBuild = project.getExtensions().getByType(MicronautBuildExtension.class);
        configurePreReleaseCheck(project);
        plugins.apply(MavenPublishPlugin.class);
        String ossUser = ProviderUtils.envOrSystemProperty(providers, "SONATYPE_USERNAME", "sonatypeOssUsername", "");
        String ossPass = ProviderUtils.envOrSystemProperty(providers, "SONATYPE_PASSWORD", "sonatypeOssPassword", "");

        plugins.withPlugin("java-base", unused -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
            javaPluginExtension.withSourcesJar();
            javaPluginExtension.withJavadocJar();
            micronautBuild.getEnvironment().duringMigration(() -> tasks.withType(Javadoc.class).configureEach(task -> {
                // temporary workaround for broken docs in many modules
                task.setFailOnError(false);
            }));
            for (String taskName : EXTRA_JAR_TASKS) {
                tasks.named(taskName, Jar.class).configure(jar -> {
                    jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
                });
            }
        });


        ExtraPropertiesExtension ext = extensions.getByType(ExtraPropertiesExtension.class);
        ext.set("signing.keyId", ProviderUtils.envOrSystemProperty(providers, "GPG_KEY_ID", "signing.keyId", null));
        ext.set("signing.password", ProviderUtils.envOrSystemProperty(providers, "GPG_PASSWORD", "signing.password", null));

        var rootDir = project.getRootDir();
        Provider<String> githubSlug = ProviderUtils.fromGradleProperty(providers, rootDir,"githubSlug");
        PublishingExtension publishing = extensions.getByType(PublishingExtension.class);
        publishing.getPublications().configureEach(publication -> {
            if (publication instanceof MavenPublication) {
                MavenPublication mavenPublication = (MavenPublication) publication;
                mavenPublication.pom(pom -> {
                    pom.getName().set(ProviderUtils.fromGradleProperty(providers, rootDir, "title"));
                    pom.getDescription().set(ProviderUtils.fromGradleProperty(providers, rootDir,"projectDesc"));
                    pom.getUrl().set(ProviderUtils.fromGradleProperty(providers, rootDir,"projectUrl"));
                    pom.licenses(licenses -> {
                        licenses.license(license -> {
                            license.getName().set("The Apache Software License, Version 2.0");
                            license.getUrl().set("http://www.apache.org/licenses/LICENSE-2.0.txt");
                            license.getDistribution().set("repo");
                        });
                    });
                    pom.scm(scm -> {
                        scm.getUrl().set(githubSlug.map(s -> "scm:git@github.com:" + s + ".git"));
                        scm.getConnection().set(githubSlug.map(s -> "scm:git@github.com:" + s + ".git"));
                        scm.getDeveloperConnection().set(githubSlug.map(s -> "scm:git@github.com:" + s + ".git"));
                    });
                    pom.developers(developers -> {
                        String devs = ProviderUtils.fromGradleProperty(providers, rootDir,"developers").getOrNull();
                        if (devs != null) {
                            for (String dev : devs.split(",")) {
                                developers.developer(developer -> {
                                    developer.getId().set(dev.toLowerCase(Locale.ENGLISH).replace(" ", ""));
                                    developer.getName().set(dev);
                                });
                            }
                        }
                    });
                });
            } else {
                throw new UnsupportedOperationException("Unsupported publication type: " + publication.getClass().getName());
            }
        });

        publishing.repositories(repositories -> {
            Provider<String> externalRepoUri = providers.systemProperty("io.micronaut.publishing.uri");
            if (externalRepoUri.isPresent()) {
                repositories.maven(maven -> {
                    maven.setName("External");
                    maven.setUrl(externalRepoUri);
                    Provider<String> externalRepoUsername = providers.systemProperty("io.micronaut.publishing.username");
                    Provider<String> externalRepoPassword = providers.systemProperty("io.micronaut.publishing.password");
                    Provider<String> externalRepoAllowInsecureProtocol = providers.systemProperty("io.micronaut.publishing.allowInsecureProtocol");
                    if (externalRepoUsername.isPresent() && externalRepoPassword.isPresent()) {
                        maven.credentials(credentials -> {
                            credentials.setUsername(externalRepoUsername.get());
                            credentials.setPassword(externalRepoPassword.get());
                        });
                    }
                    if (externalRepoAllowInsecureProtocol.isPresent()) {
                        maven.setAllowInsecureProtocol(Boolean.parseBoolean(externalRepoAllowInsecureProtocol.get()));
                    }
                });
            }
            repositories.maven(maven -> {
                maven.setName("Build");
                maven.setUrl(project.getRootProject().getLayout().getBuildDirectory().dir("repo"));
            });
        });
        if (!plugins.hasPlugin("java-gradle-plugin")) {
            publishing.publications(publications -> {
                String aid = moduleNameOf(project.getName());
                publications.create("maven", MavenPublication.class, pub -> {
                    pub.setArtifactId(aid);
                    plugins.withPlugin("java", unused -> {
                        pub.from(project.getComponents().getByName("java"));
                        pub.versionMapping(mapping -> {
                            mapping.usage("java-api", usage -> usage.fromResolutionOf("runtimeClasspath"));
                            mapping.usage("java-runtime", VariantVersionMappingStrategy::fromResolutionResult);
                        });
                    });
                });
            });

            // Include a pom.xml file into the jar
            // so that automated vulnerability scanners are happy
            tasks.withType(Jar.class).configureEach(jar -> {
                if (JARS_TO_EMBED_POM.contains(jar.getName())) {
                    String aid = moduleNameOf(project.getName());
                    jar.into("META-INF/maven/" + project.getGroup() + "/" + aid, spec -> {
                        spec.from(tasks.named("generatePomFileForMavenPublication"), it -> it.rename("pom-default.xml", "pom.xml"));
                    });
                }
            });
        }

        //do not generate extra load on Nexus with new staging repository if signing fails
        tasks.withType(InitializeNexusStagingRepository.class).configureEach(t -> t.shouldRunAfter(tasks.withType(Sign.class)));

        File secRingFile = new File(project.getRootDir(), "secring.gpg");
        if (!secRingFile.exists()) {
            secRingFile = new File(System.getenv("HOME") + "/.gnupg/secring.gpg");
        }
        if (secRingFile.exists()) {
            ext.set("signing.secretKeyRingFile", secRingFile.getAbsolutePath());
        }

        if (!ossUser.isEmpty() && !ossPass.isEmpty()) {
            boolean hasKeyId = ext.getProperties().get("signing.keyId") != null;
            if (hasKeyId) {
                plugins.apply("signing");
                SigningExtension signing = extensions.getByType(SigningExtension.class);
                if (project.hasProperty("signing.useGpg")) {
                    // Used in local testing
                    signing.useGpgCmd();
                }
                signing.setRequired(shouldSign(project));
                publishing.getPublications().all(signing::sign);
                project.getTasks().withType(Sign.class, sign -> sign.onlyIf(t -> shouldSign(project)));
            }

        }
    }

    private static boolean isPublishingDisabledFor(Project project) {
        Object p = project.findProperty("micronautPublish");
        // add option to skip publishing
        if (p == null) {
            if ((project.getName().contains("doc") && !project.getName().contains("adoc")) || project.getName().contains("example")) {
                project.getLogger().info("Publishing is disabled for project {}", project.getName());
                return true;
            }
        } else {
            boolean doPublish = Boolean.valueOf(p.toString()) == Boolean.TRUE;
            if (!doPublish) {
                project.getLogger().info("Publishing is explicitly disabled for project {}", project.getName());
                return true;
            }
        }
        return false;
    }

    private static boolean shouldSign(Project project) {
        return !project.getVersion().toString().endsWith("-SNAPSHOT") && !project.hasProperty("skipSigning");
    }

    private void configurePreReleaseCheck(Project project) {
        project.getTasks().register(PRE_RELEASE_CHECK_TASK_NAME, task -> task.setDescription("Performs pre-release checks"));
    }
}
