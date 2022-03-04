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
package io.micronaut.build;

import groovy.lang.Closure;
import groovy.namespace.QName;
import groovy.util.Node;
import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser;
import io.micronaut.build.catalogs.internal.Library;
import io.micronaut.build.pom.MicronautBomExtension;
import io.micronaut.build.pom.PomChecker;
import io.micronaut.build.pom.VersionCatalogConverter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.plugins.JavaPlatformExtension;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.plugins.catalog.CatalogPluginExtension;
import org.gradle.api.plugins.catalog.VersionCatalogPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This plugin configures a Micronaut module as a platform,
 * that is to say something which publishes a BOM file (or
 * a Gradle catalog).
 *
 * A BOM can be created from the version catalog which is
 * used to build the project itself. In this case, the
 * dependencies which must appear in the BOM have to be
 * prefixed with `managed-`.
 */
@SuppressWarnings({"UnstableApiUsage", "HardCodedStringLiteral"})
public abstract class MicronautBomPlugin implements Plugin<Project> {

    public static final List<String> DEPENDENCY_PATH = Arrays.asList("dependencyManagement", "dependencies", "dependency");

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        plugins.apply(JavaPlatformPlugin.class);
        plugins.apply(VersionCatalogPlugin.class);
        plugins.apply(MicronautBuildExtensionPlugin.class);
        plugins.apply(MicronautPublishingPlugin.class);
        MicronautBomExtension bomExtension = project.getExtensions().create("micronautBom", MicronautBomExtension.class);
        bomExtension.getPublishCatalog().convention(true);
        bomExtension.getIncludeBomInCatalog().convention(true);
        bomExtension.getImportProjectCatalog().convention(true);
        bomExtension.getExcludeProject().convention(p -> p.getName().contains("bom") || p.getName().startsWith("test-suite") || !p.getSubprojects().isEmpty());
        bomExtension.getExtraExcludedProjects().add(project.getName());
        bomExtension.getCatalogToPropertyNameOverrides().convention(Collections.emptyMap());
        bomExtension.getInlineNestedCatalogs().convention(true);
        bomExtension.getExcludedInlinedAliases().convention(Collections.emptySet());
        configureBOM(project, bomExtension);
        project.getRepositories().mavenCentral();
    }

    private static String nameOf(Node n) {
        Object name = n.name();
        if (name instanceof String) {
            return (String) name;
        }
        return ((QName) n.name()).getLocalPart();
    }

    @SuppressWarnings("unchecked")
    private static Stream<Node> forEachNode(Node node, List<String> path) {
        if (path.isEmpty()) {
            return Stream.empty();
        }
        String child = path.get(0);
        List<Node> children = (List<Node>) node.children();
        if (path.size() == 1) {
            return children.stream().filter(n -> nameOf(n).equals(child));
        } else {
            return children
                    .stream()
                    .filter(n -> nameOf(n).equals(child))
                    .flatMap(n -> forEachNode(n, path.subList(1, path.size())));

        }
    }

    @SuppressWarnings("unchecked")

    private Node childOf(Node node, String name) {
        List<Node> children = (List<Node>) node.children();
        return children.stream().filter(n -> nameOf(n).equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String removePrefix(String str, String prefix) {
        if (str.startsWith(prefix)) {
            return str.substring(prefix.length());
        }
        return str;
    }

    private static String toPropertyName(String alias) {
        return Arrays.stream(alias.split("(?=[A-Z])"))
                .map(s -> s.toLowerCase(Locale.US))
                .collect(Collectors.joining("-"))
                .replace('-', '.');
    }

    private String bomPropertyName(MicronautBomExtension ext, String alias) {
        alias = removePrefix(alias, "managed.");
        alias = removePrefix(alias, "boms.");
        String baseName = ext.getCatalogToPropertyNameOverrides().getting(alias).getOrElse(toPropertyName(alias));
        return baseName + ".version";
    }

    private static void forEachProject(MicronautBomExtension ext, Project project, Consumer<? super Project> consumer) {
        Set<String> excludedProjects = ext.getExtraExcludedProjects().get();
        Spec<? super Project> excludeSpec = ext.getExcludeProject().get();
        for (Project p : project.getRootProject().getSubprojects()) {
            if (excludeSpec.isSatisfiedBy(p) || excludedProjects.contains(p.getName())) {
                continue;
            }
            consumer.accept(p);
        }
    }

    private static String assertVersion(Project p) {
        String version = String.valueOf(p.getVersion());
        if (version.isEmpty() || "unspecified".equals(version)) {
            throw new GradleException("Version of " + p.getPath() + " is undefined!");
        }
        return version;
    }

    private void configureBOM(Project project, MicronautBomExtension bomExtension) {
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        JavaPlatformExtension javaPlatformExtension = project.getExtensions().getByType(JavaPlatformExtension.class);
        javaPlatformExtension.allowDependencies();
        publishing.getPublications().create("maven", MavenPublication.class);
        TaskContainer tasks = project.getTasks();
        project.afterEvaluate(unused -> configureLate(project, bomExtension, publishing, tasks));

        registerCheckBomTask(project, publishing, tasks);

    }

    private void configureLate(Project project, MicronautBomExtension bomExtension, PublishingExtension publishing, TaskContainer tasks) {
        String publishedName = "micronaut-" + project.getName();
        String group = String.valueOf(project.getGroup());
        Optional<VersionCatalog> versionCatalog = findVersionCatalog(project, bomExtension);
        final VersionCatalogConverter modelConverter = new VersionCatalogConverter(
                project.getRootProject().file("gradle/libs.versions.toml"),
                project.getExtensions().findByType(CatalogPluginExtension.class)
        );
        tasks.named("generateCatalogAsToml", task -> modelConverter.populateModel());
        if (bomExtension.getPublishCatalog().get()) {
            configureVersionCatalog(project, bomExtension, publishedName, group);
        }
        publishing.getPublications().named("maven", MavenPublication.class, pub -> {
            pub.setArtifactId(publishedName);
            pub.from(project.getComponents().getByName("javaPlatform"));
            pub.pom(pom -> {
                pom.setPackaging("pom");
                pom.withXml(xml -> {
                    Node node = xml.asNode();
                    Optional<Node> packagingNode = Optional.ofNullable(childOf(node, "packaging"));
                    if (project.hasProperty("pomInfo")) {
                        packagingNode.ifPresent(packaging -> packaging.plus((Closure) project.findProperty("pomInfo")));
                    }
                    modelConverter.getModel().getLibrariesTable().forEach(library -> {
                        String alias = Optional.ofNullable(library.getVersion().getReference()).map(a -> a.replace('-', '.')).orElse("");
                        String libraryAlias = Optional.ofNullable(library.getAlias()).map(a -> a.replace('-', '.')).orElse("");
                        if (libraryAlias.startsWith("managed.") || libraryAlias.startsWith("boms.")) {
                            Optional<Node> pomDep = forEachNode(node, DEPENDENCY_PATH)
                                    .filter(n ->
                                            childOf(n, "artifactId").text().equals(library.getName()) &&
                                                    childOf(n, "groupId").text().equals(library.getGroup()))
                                    .findFirst();
                            if (pomDep.isPresent()) {
                                String bomPropertyName = bomPropertyName(bomExtension, alias);
                                childOf(pomDep.get(), "version").setValue("${" + bomPropertyName + "}");
                            } else {
                                System.err.println("[WARNING] Didn't find library " + library.getGroup() + ":" + library.getName() + " in BOM file");
                            }
                        }
                    });

                    // Add individual module versions as properties
                    forEachProject(bomExtension, project, p -> {
                        String propertyName = "micronaut." + p.getName().replace('-', '.') + ".version";
                        String projectGroup = String.valueOf(p.getGroup());
                        Optional<Node> pomDep = forEachNode(node, DEPENDENCY_PATH)
                                .filter(n -> childOf(n, "artifactId").text().equals("micronaut-" + p.getName()) &&
                                        childOf(n, "groupId").text().equals(projectGroup))
                                .findFirst();
                        if (pomDep.isPresent()) {
                            childOf(pomDep.get(), "version").setValue("${" + propertyName + "}");
                        } else {
                            System.err.println("[WARNING] Didn't find dependency " + projectGroup + ":micronaut-" + p.getName() + " in BOM file");
                        }
                    });
                });
                versionCatalog.ifPresent(libsCatalog -> libsCatalog.getVersionAliases().forEach(alias -> {
                    if (alias.startsWith("managed.")) {
                        libsCatalog.findVersion(alias).ifPresent(version -> {
                            String propertyName = bomPropertyName(bomExtension, alias);
                            pom.getProperties().put(propertyName, version.getRequiredVersion());
                        });
                    }
                }));
                forEachProject(bomExtension, project, p -> {
                    project.evaluationDependsOn(p.getPath());
                    String propertyName = "micronaut." + p.getName().replace('-', '.') + ".version";
                    pom.getProperties().put(propertyName, assertVersion(p));
                });
            });
        });

        Configuration api = project.getConfigurations().getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME);
        Configuration catalogs = project.getConfigurations().detachedConfiguration();
        catalogs.attributes(attrs -> {
            attrs.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.REGULAR_PLATFORM));
            attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.VERSION_CATALOG));
        });
        versionCatalog.ifPresent(libsCatalog -> libsCatalog.getLibraryAliases().forEach(alias -> {
            if (alias.startsWith("boms.")) {
                Dependency bomDependency = project.getDependencies().platform(libsCatalog.findLibrary(alias)
                        .map(Provider::get)
                        .orElseThrow(() -> new RuntimeException("Unexpected missing alias in catalog: " + alias))
                );
                api.getDependencies().add(bomDependency);
                catalogs.getDependencies().add(bomDependency);
            } else if (alias.startsWith("managed.")) {
                api.getDependencyConstraints().add(
                        project.getDependencies().getConstraints().create(libsCatalog.findLibrary(alias).map(Provider::get)
                                .orElseThrow(() -> new RuntimeException("Unexpected missing alias in catalog: " + alias))));
            }
        }));
        modelConverter.afterBuildingModel(builderState -> maybeInlineNestedCatalogs(bomExtension, catalogs, builderState));
        forEachProject(bomExtension, project, p -> {
            project.evaluationDependsOn(p.getPath());
            String moduleGroup = String.valueOf(p.getGroup());
            String moduleName = "micronaut-" + p.getName();
            String moduleVersion = assertVersion(p);

            api.getDependencyConstraints().add(
                    project.getDependencies()
                            .getConstraints()
                            .create(moduleGroup + ":" + moduleName + ":" + moduleVersion)
            );

            modelConverter.getExtraVersions().put(moduleName, moduleVersion);
            modelConverter.getExtraLibraries().put(moduleName, VersionCatalogConverter.library(moduleGroup, moduleName, moduleName));
        });
    }

    private void maybeInlineNestedCatalogs(MicronautBomExtension bomExtension, Configuration catalogs, VersionCatalogConverter.BuilderState builderState) {
        if (bomExtension.getInlineNestedCatalogs().get()) {
            VersionCatalogBuilder builder = builderState.getBuilder();
            Set<String> knownAliases = builderState.getKnownAliases();
            Set<String> excludeFromInlining = bomExtension.getExcludedInlinedAliases().get();
            catalogs.getIncoming()
                    .artifactView(spec -> spec.lenient(true))
                    .getFiles()
                    .forEach(catalogFile -> {
                        try (FileInputStream fis = new FileInputStream(catalogFile)) {
                            LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
                            parser.parse(fis);
                            Set<Library> librariesTable = parser.getModel().getLibrariesTable();
                            librariesTable.forEach(library -> {
                                String alias = library.getAlias();
                                if (!knownAliases.contains(alias) && !excludeFromInlining.contains(alias)) {
                                    knownAliases.add(alias);
                                    builder.library(alias, library.getGroup(), library.getName())
                                            .withoutVersion();
                                }
                            });
                        } catch (IOException e) {
                            System.err.println("Unable to parse version catalog file: " + catalogFile);
                        }
                    });
        }
    }

    private void registerCheckBomTask(Project project, PublishingExtension publishing, TaskContainer tasks) {
        TaskProvider<PomChecker> checkBom = tasks.register("checkBom", PomChecker.class, task -> {
            String repoUrl = "https://repo.maven.apache.org/maven2/";
            ArtifactRepository repo = publishing.getRepositories().findByName("Build");
            if (repo instanceof MavenArtifactRepository) {
                repoUrl = ((MavenArtifactRepository) repo).getUrl().toString();
            }
            task.getRepositories().add(repoUrl);
            task.getPomFile().fileProvider(tasks.named("generatePomFileForMavenPublication", GenerateMavenPom.class).map(GenerateMavenPom::getDestination));
            String version = assertVersion(project);
            task.getPomCoordinates().set(String.format("{}:{}:{}", project.getGroup(), "micronaut-" + project.getName(), version));
            task.getCheckBomContents().set(false);
            task.getReport().set(project.getLayout().getBuildDirectory().file("reports/boms/micronaut-bom.txt"));
            task.getFailOnSnapshots().set(!version.endsWith("-SNAPSHOT"));
            task.getFailOnError().set(true);
        });

        tasks.named("check", task -> task.dependsOn(checkBom));
    }

    private static Optional<VersionCatalog> findVersionCatalog(Project project, MicronautBomExtension bomExtension) {
        if (!bomExtension.getImportProjectCatalog().get()) {
            return Optional.empty();
        }
        VersionCatalogsExtension versionCatalogsExtension = project.getExtensions().findByType(VersionCatalogsExtension.class);
        return Optional.ofNullable(versionCatalogsExtension).map(e -> e.named("libs"));
    }

    private void configureVersionCatalog(Project project, MicronautBomExtension bomExtension, String publishedName, String group) {
        if (bomExtension.getIncludeBomInCatalog().get()) {
            CatalogPluginExtension catalog = project.getExtensions().getByType(CatalogPluginExtension.class);
            catalog.versionCatalog(vc -> {
                String versionName = publishedName.replace('-', '.');
                vc.alias(publishedName).to(group, publishedName).versionRef(versionName);
                vc.version(versionName, String.valueOf(project.getVersion()));
            });
        }
        AdhocComponentWithVariants javaPlatform = (AdhocComponentWithVariants) project.getComponents().getByName("javaPlatform");
        javaPlatform.addVariantsFromConfiguration(project.getConfigurations().getByName(VersionCatalogPlugin.VERSION_CATALOG_ELEMENTS), details -> {
            details.mapToMavenScope("compile");
            details.mapToOptional();
        });
    }


}
