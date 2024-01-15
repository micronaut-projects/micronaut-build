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

import groovy.namespace.QName;
import groovy.util.Node;
import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser;
import io.micronaut.build.catalogs.internal.Library;
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel;
import io.micronaut.build.catalogs.internal.VersionModel;
import io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin;
import io.micronaut.build.pom.MicronautBomExtension;
import io.micronaut.build.pom.PomChecker;
import io.micronaut.build.pom.PomCheckerUtils;
import io.micronaut.build.pom.VersionCatalogConverter;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.FileCollection;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.plugins.JavaPlatformExtension;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.plugins.catalog.CatalogPluginExtension;
import org.gradle.api.plugins.catalog.VersionCatalogPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
public abstract class MicronautBomPlugin implements MicronautPlugin<Project> {

    public static final List<String> DEPENDENCY_PATH = Arrays.asList("dependencyManagement", "dependencies", "dependency");

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        plugins.apply(JavaPlatformPlugin.class);
        plugins.apply(VersionCatalogPlugin.class);
        plugins.apply(MicronautBuildExtensionPlugin.class);
        plugins.apply(MicronautPublishingPlugin.class);
        plugins.apply(MicronautDependencyResolutionConfigurationPlugin.class);
        plugins.apply(MicronautBinaryCompatibilityPlugin.class);
        MicronautBomExtension bomExtension = project.getExtensions().create("micronautBom", MicronautBomExtension.class);
        bomExtension.getPublishCatalog().convention(true);
        bomExtension.getIncludeBomInCatalog().convention(true);
        bomExtension.getImportProjectCatalog().convention(true);
        bomExtension.getExcludeProject().convention(p -> p.getName().contains("bom") || p.getName().startsWith(TEST_SUITE_PROJECT_PREFIX) || !p.getSubprojects().isEmpty());
        bomExtension.getExtraExcludedProjects().add(project.getName());
        bomExtension.getCatalogToPropertyNameOverrides().convention(Collections.emptyMap());
        bomExtension.getInlineNestedCatalogs().convention(true);
        bomExtension.getExcludedInlinedAliases().convention(Collections.emptySet());
        bomExtension.getInferProjectsToInclude().convention(true);
        configureBOM(project, bomExtension);
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

    private static List<ProjectDescriptor> computeProjectDescriptors(
        MicronautBomExtension ext,
        Project project,
        Set<String> includedProjects,
        Set<String> skippedProjects
    ) {
        List<ProjectDescriptor> result = new ArrayList<>();
        boolean inferProjectsToInclude = ext.getInferProjectsToInclude().getOrElse(true);
        Set<String> excludedProjects = ext.getExtraExcludedProjects().get();
        Spec<? super Project> excludeSpec = ext.getExcludeProject().get();
        for (Project p : project.getRootProject().getSubprojects()) {
            if (p.equals(project) || excludeSpec.isSatisfiedBy(p) || excludedProjects.contains(p.getName())) {
                continue;
            }
            project.evaluationDependsOn(p.getPath());
            if (!inferProjectsToInclude || p.getPlugins().hasPlugin(MicronautPublishingPlugin.class)) {
                includedProjects.add(p.getPath());
                result.add(ProjectDescriptor.fromProject(p));
            } else {
                skippedProjects.add(p.getPath());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private void configureBOM(Project project, MicronautBomExtension bomExtension) {
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        JavaPlatformExtension javaPlatformExtension = project.getExtensions().getByType(JavaPlatformExtension.class);
        javaPlatformExtension.allowDependencies();
        TaskContainer tasks = project.getTasks();
        project.afterEvaluate(unused -> configureLate(project, bomExtension, publishing, tasks));

        registerCheckBomTask(project, publishing, bomExtension);

    }

    private void configureLate(Project project, MicronautBomExtension bomExtension, PublishingExtension publishing, TaskContainer tasks) {
        String mainProjectId = bomExtension.getPropertyName().getOrElse(project.getRootProject().getName().replace("-parent", "").replace('-', '.'));
        String publishedName = MicronautPlugin.moduleNameOf(project.getName());
        String group = String.valueOf(project.getGroup());
        Optional<VersionCatalog> versionCatalog = findVersionCatalog(project, bomExtension);
        final VersionCatalogConverter modelConverter = new VersionCatalogConverter(
            project.getRootProject().file("gradle/libs.versions.toml"),
            project.getExtensions().findByType(CatalogPluginExtension.class)
        );
        tasks.named("generateCatalogAsToml", task -> modelConverter.populateModel());
        if (bomExtension.getPublishCatalog().get()) {
            configureVersionCatalog(project, bomExtension, publishedName, group, mainProjectId);
        }
        Provider<VersionCatalogTomlModel> modelProvider = project.provider(modelConverter::getModel);
        Set<String> includedProjects = new HashSet<>();
        Set<String> skippedProjects = new HashSet<>();
        Provider<List<ProjectDescriptor>> projectDescriptors = project.provider(() ->
            computeProjectDescriptors(bomExtension, project, includedProjects, skippedProjects)
        );
        Map<String, String> inlinedPomProperties = new LinkedHashMap<>();
        publishing.getPublications().named("maven", MavenPublication.class, pub -> {
            pub.setArtifactId(publishedName);
            pub.from(project.getComponents().getByName("javaPlatform"));
            pub.pom(pom -> {
                pom.setPackaging("pom");
                pom.withXml(xml -> {
                    Node node = xml.asNode();
                    modelProvider.get().getLibrariesTable().forEach(library -> {
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
                    projectDescriptors.get().forEach(p -> {
                        String propertyName = "micronaut." + mainProjectId + ".version";
                        String projectGroup = p.getGroupId();
                        String moduleName = p.getArtifactId();
                        Optional<Node> pomDep = forEachNode(node, DEPENDENCY_PATH)
                            .filter(n -> childOf(n, "artifactId").text().equals(moduleName) &&
                                         childOf(n, "groupId").text().equals(projectGroup))
                            .findFirst();
                        if (pomDep.isPresent()) {
                            childOf(pomDep.get(), "version").setValue("${" + propertyName + "}");
                        } else {
                            System.err.println("[WARNING] Didn't find dependency " + projectGroup + ":" + moduleName + " in BOM file");
                        }
                    });
                    // Add extra versions as properties
                    var propertiesNode = childOf(node, "properties");
                    inlinedPomProperties.forEach((moduleName, version) -> {
                        String propertyName = bomPropertyName(bomExtension, moduleName);
                        var existingProperty = childOf(propertiesNode, propertyName);
                        if (existingProperty == null) {
                            propertiesNode.appendNode(propertyName, version);
                        }
                    });
                    propertiesNode.children().sort((o1, o2) -> {
                        String name1 = nameOf((Node) o1);
                        String name2 = nameOf((Node) o2);
                        return name1.compareTo(name2);
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
                projectDescriptors.get().forEach(p -> {
                    project.evaluationDependsOn(p.getPath());
                    String propertyName = "micronaut." + mainProjectId + ".version";
                    pom.getProperties().put(propertyName, PomCheckerUtils.assertVersion(p.getVersion(), p.getPath()));
                });

                tasks.withType(GenerateMavenPom.class).configureEach(pomTask -> {
                    //noinspection Convert2Lambda
                    pomTask.doLast(new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            System.out.println("Projects included into BOM:\n" + includedProjects.stream()
                                .map(p -> "    - " + p)
                                .collect(Collectors.joining("\n"))
                            );
                            if (!skippedProjects.isEmpty()) {
                                System.out.println("Skipped projects which do not apply the publishing plugin:\n" + skippedProjects.stream()
                                    .map(p -> "    - " + p)
                                    .collect(Collectors.joining("\n"))
                                );
                            }
                        }
                    });
                });
            });
        });

        Configuration api = project.getConfigurations().getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME);
        Configuration runtime = project.getConfigurations().getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME);
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
        // the following properties are captures _outside_ of the lambda
        // to avoid it referencing `bomExtension` or `catalogs`
        FileCollection catalogFiles = catalogs.getIncoming()
            .artifactView(spec -> spec.lenient(true))
            .getFiles();
        Property<Boolean> inlineNestedCatalogs = bomExtension.getInlineNestedCatalogs();
        SetProperty<String> excludedInlinedAliases = bomExtension.getExcludedInlinedAliases();
        modelConverter.afterBuildingModel(builderState -> {
            api.getAllDependencyConstraints().forEach(MicronautBomPlugin::checkVersionConstraint);
            runtime.getAllDependencyConstraints().forEach(MicronautBomPlugin::checkVersionConstraint);
            maybeInlineNestedCatalogs(catalogFiles, builderState, inlineNestedCatalogs, excludedInlinedAliases, inlinedPomProperties);
        });
        projectDescriptors.get().forEach(p -> {
            String moduleGroup = p.getGroupId();
            String moduleName = p.getArtifactId();
            String moduleVersion = PomCheckerUtils.assertVersion(p.getVersion(), p.getPath());

            api.getDependencyConstraints().add(
                project.getDependencies()
                    .getConstraints()
                    .create(moduleGroup + ":" + moduleName + ":" + moduleVersion)
            );

            String mainModuleName = MicronautPlugin.moduleNameOf(mainProjectId.replace('.', '-'));
            modelConverter.getExtraVersions().put(mainModuleName, moduleVersion);
            modelConverter.getExtraLibraries().put(moduleName, VersionCatalogConverter.library(moduleGroup, moduleName, mainModuleName));
        });
    }

    private void maybeInlineNestedCatalogs(FileCollection catalogs,
                                           VersionCatalogConverter.BuilderState builderState,
                                           Property<Boolean> inlineNestedCatalogs,
                                           SetProperty<String> excludedInlinedAliases,
                                           Map<String, String> inlinedPomProperties) {
        if (Boolean.TRUE.equals(inlineNestedCatalogs.get())) {
            VersionCatalogBuilder builder = builderState.getBuilder();
            Map<String, VersionCatalogConverter.AliasRecord> knownAliases = builderState.getKnownAliases();
            Map<String, VersionCatalogConverter.AliasRecord> knownVersionAliases = builderState.getKnownVersionAliases();
            Set<String> excludeFromInlining = excludedInlinedAliases.get();
            catalogs.forEach(catalogFile -> {
                try (FileInputStream fis = new FileInputStream(catalogFile)) {
                    LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
                    parser.parse(fis);
                    Set<Library> librariesTable = parser.getModel().getLibrariesTable();
                    Set<VersionModel> versionsTable = parser.getModel().getVersionsTable();
                    librariesTable.forEach(library -> {
                        String alias = library.getAlias();
                        if (!excludeFromInlining.contains(alias)) {
                            String source = catalogFile.getName();
                            if (!knownAliases.containsKey(alias)) {
                                String reference = library.getVersion().getReference();
                                String version = null;
                                if (reference != null) {
                                    version = reference;
                                    if (!knownVersionAliases.containsKey(reference)) {
                                        var requiredVersion = versionsTable.stream().filter(m -> reference.equals(m.getReference())).findFirst().get().getVersion().getRequire();
                                        if (requiredVersion != null) {
                                            builder.version(reference, requiredVersion);
                                            inlinedPomProperties.put(reference, requiredVersion);
                                        } else {
                                            throw new IllegalStateException("Version '" + reference + "' is not defined as a required version in the catalog");
                                        }
                                    } else {
                                        Set<String> sources = knownVersionAliases.get(reference).getSources();
                                        if (!sources.equals(Collections.singleton(source))) {
                                            System.err.println("[Warning] While inlining " + source + ", version alias '" + alias + "' is already defined in the catalog by " + sources + " so it won't be imported");
                                        }
                                    }
                                    knownVersionAliases.get(reference).addSource(source);
                                }
                                VersionCatalogBuilder.LibraryAliasBuilder libraryBuilder = builder.library(alias, library.getGroup(), library.getName());
                                if (version != null) {
                                    libraryBuilder.versionRef(reference);
                                } else {
                                    libraryBuilder.withoutVersion();
                                }
                            } else {
                                VersionCatalogConverter.AliasRecord record = knownAliases.get(alias);
                                // There is one case where we don't want to warn: the main BOM file will have
                                // managed version for all Micronaut BOM files, but the version catalogs of these
                                // imported files will also have an alias with the same name
                                boolean warn = true;
                                if (source.startsWith("micronaut-") && source.contains("-bom")) {
                                    String shortName = source.substring(0, source.indexOf("-bom"));
                                    if (alias.equals(shortName) && record.getSources().equals(Collections.singleton(VersionCatalogConverter.MAIN_ALIASES_SOURCE))) {
                                        warn = false;
                                    }
                                }
                                if (warn) {
                                    System.err.println("[Warning] While inlining " + source + ", alias '" + alias + "' is already defined in the catalog by " + record.getSources() + " so it won't be imported");
                                }
                            }
                            knownAliases.get(alias).addSource(source);
                        }
                    });
                } catch (IOException e) {
                    System.err.println("Unable to parse version catalog file: " + catalogFile);
                }
            });
        }
    }

    private void registerCheckBomTask(Project project, PublishingExtension publishing, MicronautBomExtension bomExtension) {
        TaskProvider<PomChecker> checkBom = PomCheckerUtils.registerPomChecker("checkBom", project, publishing, task -> {
            task.getSuppressions().convention(bomExtension.getSuppressions());
        });
        // Add a convenience task to so that we can run `checkPom` and it will run `checkBom` as well
        project.getTasks().register("checkPom", task -> task.dependsOn(checkBom));
    }

    private static Optional<VersionCatalog> findVersionCatalog(Project project, MicronautBomExtension bomExtension) {
        if (!bomExtension.getImportProjectCatalog().get()) {
            return Optional.empty();
        }
        VersionCatalogsExtension versionCatalogsExtension = project.getExtensions().findByType(VersionCatalogsExtension.class);
        return Optional.ofNullable(versionCatalogsExtension).map(e -> e.named("libs"));
    }

    private void configureVersionCatalog(Project project, MicronautBomExtension bomExtension, String publishedName, String group, String mainProjectId) {
        if (bomExtension.getIncludeBomInCatalog().get()) {
            CatalogPluginExtension catalog = project.getExtensions().getByType(CatalogPluginExtension.class);
            catalog.versionCatalog(vc -> {
                String mainModuleName = MicronautPlugin.moduleNameOf(mainProjectId);
                String versionName = mainModuleName.replace('-', '.');
                vc.library(publishedName, group, publishedName).versionRef(versionName);
                vc.version(versionName, String.valueOf(project.getVersion()));
            });
        }
        AdhocComponentWithVariants javaPlatform = (AdhocComponentWithVariants) project.getComponents().getByName("javaPlatform");
        javaPlatform.addVariantsFromConfiguration(project.getConfigurations().getByName(VersionCatalogPlugin.VERSION_CATALOG_ELEMENTS), details -> {
            details.mapToMavenScope("compile");
            details.mapToOptional();
        });
    }

    private static void checkVersionConstraint(DependencyConstraint constraint) {
        VersionConstraint versionConstraint = constraint.getVersionConstraint();
        if (versionConstraint.getRequiredVersion().isEmpty()
            && versionConstraint.getPreferredVersion().isEmpty()
            && versionConstraint.getStrictVersion().isEmpty()
            && versionConstraint.getRejectedVersions().isEmpty()) {
            throw new InvalidUserDataException("A dependency constraint was added on '" + constraint.getModule() + "' without a version. This is invalid: a constraint must specify a version.");
        }
    }

    static class ProjectDescriptor {
        private final String path;
        private final String groupId;
        private final String artifactId;
        private final String version;

        static ProjectDescriptor fromProject(Project project) {
            return new ProjectDescriptor(
                project.getPath(),
                String.valueOf(project.getGroup()),
                MicronautPlugin.moduleNameOf(project.getName()),
                String.valueOf(project.getVersion())
            );
        }

        private ProjectDescriptor(String path, String groupId, String artifactId, String version) {
            this.path = path;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getPath() {
            return path;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }
    }
}
