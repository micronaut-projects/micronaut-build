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
import io.micronaut.build.catalogs.internal.Plugin;
import io.micronaut.build.catalogs.internal.VersionCatalogTomlModel;
import io.micronaut.build.catalogs.internal.VersionModel;
import io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin;
import io.micronaut.build.pom.InterceptedVersionCatalogBuilder;
import io.micronaut.build.pom.MicronautBomExtension;
import io.micronaut.build.pom.PomChecker;
import io.micronaut.build.pom.PomCheckerUtils;
import io.micronaut.build.pom.VersionCatalogConverter;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.composition.DefaultDependencyManagementImporter;
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler;
import org.apache.maven.model.interpolation.DefaultModelVersionProcessor;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.locator.DefaultModelLocator;
import org.apache.maven.model.management.DefaultDependencyManagementInjector;
import org.apache.maven.model.management.DefaultPluginManagementInjector;
import org.apache.maven.model.normalization.DefaultModelNormalizer;
import org.apache.maven.model.path.DefaultModelPathTranslator;
import org.apache.maven.model.path.DefaultModelUrlNormalizer;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.model.path.ProfileActivationFilePathInterpolator;
import org.apache.maven.model.plugin.DefaultReportingConverter;
import org.apache.maven.model.profile.DefaultProfileSelector;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.superpom.DefaultSuperPomProvider;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.plugins.JavaPlatformExtension;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.plugins.catalog.CatalogPluginExtension;
import org.gradle.api.plugins.catalog.VersionCatalogPlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(MicronautBomPlugin.class);

    private static final Pattern BASENAME_EXTRACTOR = Pattern.compile("^([a-zA-Z0-9-]+?)-\\d[\\d.-]*(-[a-zA-Z0-9]+)?.+$");
    public static final List<String> DEPENDENCY_PATH = Arrays.asList("dependencyManagement", "dependencies", "dependency");
    public static final String BOM_VERSION_INFERENCE_CONFIGURATION_NAME = "bomVersionInference";
    public static final String EXTRA_BOMS_INLINING_CONFIGURATION_NAME = "extraBomsInlining";
    public static final String ALL_BOMS_CONFIGURATION_NAME = "allBoms";
    public static final String CATALOGS_INLINING_CONFIGURATION_NAME = "inlinedCatalogs";

    private ModelResolver mavenModelResolver;

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
        bomExtension.getExcludedInlinedAliases().convention(Set.of());
        bomExtension.getInlineRegularBOMs().convention(false);
        bomExtension.getInferProjectsToInclude().convention(true);
        bomExtension.getCatalogName().convention("libs");
        configureBOM(project, bomExtension);
        mavenModelResolver = new SimpleMavenModelResolver(project.getConfigurations(), project.getDependencies());
        createHelperConfigurations(project);
    }

    private static void createHelperConfigurations(Project project) {
        project.getConfigurations().create(BOM_VERSION_INFERENCE_CONFIGURATION_NAME, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
        });
        project.getConfigurations().create(ALL_BOMS_CONFIGURATION_NAME, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
        });
        project.getConfigurations().create(EXTRA_BOMS_INLINING_CONFIGURATION_NAME, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
        });
        project.getConfigurations().create(CATALOGS_INLINING_CONFIGURATION_NAME, catalogs -> {
            catalogs.setCanBeResolved(true);
            catalogs.setCanBeConsumed(false);
            catalogs.attributes(attrs -> {
                attrs.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.REGULAR_PLATFORM));
                attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.VERSION_CATALOG));
            });
        });
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
            project.getRootProject().file("gradle/" + bomExtension.getCatalogName().get() + ".versions.toml"),
            project.getExtensions().findByType(CatalogPluginExtension.class)
        );
        var libraryDefinitions = new ArrayList<InterceptedVersionCatalogBuilder.LibraryDefinition>();
        modelConverter.onLibrary(libraryDefinitions::add);
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
        List<InlinedDependency> inlinedMavenDependencies = new ArrayList<>();
        var logFile = prepareLogFile(project);
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
                    var dependencyManagementNode = childOf(node, "dependencyManagement");
                    var dependencyManagementDependenciesNode = childOf(dependencyManagementNode, "dependencies");
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
                    // add inlined Maven dependencies (issue #689)
                    inlinedMavenDependencies.forEach(dep -> {
                        var dependencyNode = new Node(null, "dependency");
                        dependencyNode.append(new Node(null, "groupId", dep.groupId));
                        dependencyNode.append(new Node(null, "artifactId", dep.artifactId));
                        dependencyNode.append(new Node(null, "version", "${" + dep.versionProperty + "}"));
                        dependencyManagementDependenciesNode.append(dependencyNode);
                    });
                    // then sort nodes so that these which have <import> scope appear last
                    makeImportedBOMsLast(dependencyManagementDependenciesNode);
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
                            System.out.println("Inlining log file: " + logFile);
                        }
                    });
                });
            });
        });

        Configuration api = project.getConfigurations().getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME);
        Configuration runtime = project.getConfigurations().getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME);
        Configuration catalogs = project.getConfigurations().getByName(CATALOGS_INLINING_CONFIGURATION_NAME);
        Configuration allBoms = project.getConfigurations().getByName(ALL_BOMS_CONFIGURATION_NAME);
        versionCatalog.ifPresent(libsCatalog -> libsCatalog.getLibraryAliases().forEach(alias -> {
            if (alias.startsWith("boms.")) {
                var catalogEntry = libsCatalog.findLibrary(alias)
                    .map(Provider::get)
                    .orElseThrow(() -> new RuntimeException("Unexpected missing alias in catalog: " + alias));
                var catalog = project.getDependencies().platform(catalogEntry);
                api.getDependencies().add(catalog);
                catalogs.getDependencies().add(catalog);
                Dependency bomDependency = project.getDependencies().create(catalogEntry);
                if (bomDependency instanceof ExternalModuleDependency emd) {
                    emd.artifact(a -> a.setExtension("pom"));
                }
                allBoms.getDependencies().add(bomDependency);
            } else if (alias.startsWith("managed.")) {
                api.getDependencyConstraints().add(
                    project.getDependencies().getConstraints().create(libsCatalog.findLibrary(alias).map(Provider::get)
                        .orElseThrow(() -> new RuntimeException("Unexpected missing alias in catalog: " + alias))));
            }
        }));
        // the following properties are captures _outside_ of the lambda
        // to avoid it referencing `bomExtension` or `catalogs`
        var catalogArtifactView = catalogs.getIncoming()
            .artifactView(spec -> spec.lenient(true));
        var catalogArtifacts = catalogArtifactView.getArtifacts().getArtifacts();
        var bomArtifacts = allBoms.getIncoming()
            .artifactView(spec -> spec.lenient(true))
            .getArtifacts().getArtifacts();
        Property<Boolean> inlineNestedCatalogs = bomExtension.getInlineNestedCatalogs();
        Property<Boolean> inlineNestedBOMs = bomExtension.getInlineRegularBOMs();
        var excludedInlinedAliases = bomExtension.getExcludeFromInlining()
            .zip(bomExtension.getExcludedInlinedAliases(), (excludeMap, simpleExcludes) -> {
                Map<String, Set<String>> result = new HashMap<>(excludeMap);
                simpleExcludes.forEach(e -> result.computeIfAbsent("*", k -> new HashSet<>()).add(e));
                return result;
            });
        var includedAliases = bomExtension.getInlinedAliases();
        var knownLibraries = new ArrayList<String>();
        modelConverter.onLibrary(l -> knownLibraries.add(l.groupId() + ":" + l.artifactId()));
        modelConverter.afterBuildingModel(builderState -> {
            api.getAllDependencyConstraints().forEach(MicronautBomPlugin::checkVersionConstraint);
            runtime.getAllDependencyConstraints().forEach(MicronautBomPlugin::checkVersionConstraint);
            try (var log = new PrintWriter(Files.newBufferedWriter(logFile))) {
                maybeInlineNestedCatalogs(log, catalogArtifacts, bomArtifacts, builderState, inlineNestedCatalogs, inlineNestedBOMs, excludedInlinedAliases, includedAliases, inlinedPomProperties, inlinedMavenDependencies, project);
                performVersionInference(log, project, bomExtension, builderState, libraryDefinitions, inlinedPomProperties, inlinedMavenDependencies, project.getConfigurations().findByName(BOM_VERSION_INFERENCE_CONFIGURATION_NAME), knownLibraries);
                var unresolvedDependencies = new LinkedHashSet<ComponentSelector>();
                catalogs.getIncoming()
                    .getResolutionResult()
                    .allDependencies(dep -> {
                        if (dep instanceof UnresolvedDependencyResult unresolved) {
                            unresolvedDependencies.add(unresolved.getRequested());
                        }
                    });
                allBoms.getIncoming()
                    .getResolutionResult()
                    .allDependencies(dep -> {
                        if (dep instanceof UnresolvedDependencyResult unresolved) {
                            unresolvedDependencies.add(unresolved.getRequested());
                        }
                    });
                if (!unresolvedDependencies.isEmpty()) {
                    log.println("[WARNING] There were unresolved dependencies during inlining! This may cause incomplete catalogs!");
                    for (ComponentSelector unresolvedDependency : unresolvedDependencies) {
                        log.println("   " + unresolvedDependency);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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

    private static void performVersionInference(PrintWriter log,
                                                Project project,
                                                MicronautBomExtension bomExtension,
                                                VersionCatalogConverter.BuilderState builderState,
                                                List<InterceptedVersionCatalogBuilder.LibraryDefinition> libraryDefinitions,
                                                Map<String, String> inlinedPomProperties,
                                                List<InlinedDependency> inlinedMavenDependencies,
                                                Configuration versionInferenceConfiguration,
                                                List<String> knownLibrariesList) {
        // This copy serves both optimization (search in set instead of list) but also because the
        // list which is passed as an input will be augmented by this very method, so we have to
        // create a defensive copy.
        var knownLibraries = knownLibrariesList.stream().collect(Collectors.toUnmodifiableSet());
        var inferredLibraries = bomExtension.getInferredManagedDependencies().getOrElse(Map.of());
        if (!inferredLibraries.isEmpty()) {
            Set<String> found = new HashSet<>();
            libraryDefinitions.forEach(lib -> {
                if (lib.version() != null) {
                    versionInferenceConfiguration.getDependencies().add(project.getDependencies().create(lib.toString()));
                }
            });
            var seen = new HashSet<String>();
            versionInferenceConfiguration.getIncoming()
                .getResolutionResult()
                .allDependencies(dep -> {
                    if (dep instanceof ResolvedDependencyResult resolved) {
                        if (resolved.getSelected().getId() instanceof ModuleComponentIdentifier mid) {
                            var module = mid.getModuleIdentifier().toString();
                            if (seen.add(module) && inferredLibraries.containsKey(module)) {
                                if (knownLibraries.contains(module)) {
                                    found.add(module);
                                    log.println("Library " + module + " is already present in the catalog, remove it from the inferred versions list");
                                    return;
                                }
                                found.add(module);
                                var alias = inferredLibraries.get(module);
                                if (!builderState.getKnownVersionAliases().containsKey(alias)) {
                                    builderState.getBuilder().version(alias, mid.getVersion());
                                }
                                var mavenPropertyName = toPropertyName(alias);
                                if (!inlinedPomProperties.containsKey(alias)) {
                                    inlinedPomProperties.put(mavenPropertyName, mid.getVersion());
                                }
                                if (!builderState.getKnownAliases().containsKey(alias)) {
                                    builderState.getBuilder().library(alias, mid.getGroup(), mid.getModule()).versionRef(alias);
                                }
                                var inlinedMavenDep = new InlinedDependency(mid.getGroup(), mid.getModule(), mavenPropertyName + ".version");
                                if (!inlinedMavenDependencies.contains(inlinedMavenDep)) {
                                    inlinedMavenDependencies.add(inlinedMavenDep);
                                }
                            }
                        }
                    }
                });
            // throw an error if an inferred module cannot be found
            var missing = new ArrayList<>(inferredLibraries.keySet());
            missing.removeAll(found);
            if (!missing.isEmpty()) {
                throw new RuntimeException("Some dependencies were declared as inferred, but they don't appear in the dependency graph. You must use an explicit version for these : " + missing);
            }
        }
    }

    private static @NotNull Path prepareLogFile(Project project) {
        var logFile = project.getLayout().getBuildDirectory().file("logs/inlining-" + System.currentTimeMillis() + ".log")
            .get()
            .getAsFile()
            .toPath();
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return logFile;
    }

    private void makeImportedBOMsLast(Node dependencyManagementDependenciesNode) {
        dependencyManagementDependenciesNode.children().sort((o1, o2) -> {
            var scope1 = childOf((Node) o1, "scope");
            var scope2 = childOf((Node) o2, "scope");
            if (scope1 == null && scope2 == null) {
                return 0;
            }
            if (scope1 != null && scope2 == null) {
                return 1;
            }
            if (scope1 == null) {
                return -1;
            }
            return String.valueOf(scope1.value()).compareTo(String.valueOf(scope2.value()));
        });
    }

    private void maybeInlineNestedCatalogs(PrintWriter log,
                                           Set<ResolvedArtifactResult> catalogs,
                                           Set<ResolvedArtifactResult> bomArtifacts,
                                           VersionCatalogConverter.BuilderState builderState,
                                           Property<Boolean> inlineNestedCatalogs,
                                           Property<Boolean> inlineNestedBOMs,
                                           Provider<Map<String, Set<String>>> excludedInlinedAliases,
                                           MapProperty<String, Set<String>> inlinedAliases,
                                           Map<String, String> inlinedPomProperties,
                                           List<InlinedDependency> inlinedMavenDependencies,
                                           // This last argument breaks configuration cache but for now we have no choice :(
                                           Project p) {
        if (Boolean.TRUE.equals(inlineNestedCatalogs.get())) {
            VersionCatalogBuilder builder = builderState.getBuilder();
            Map<String, VersionCatalogConverter.AliasRecord> knownAliases = builderState.getKnownAliases();
            Map<String, VersionCatalogConverter.AliasRecord> knownPluginAliases = builderState.getKnownPluginAliases();
            Map<String, VersionCatalogConverter.AliasRecord> knownVersionAliases = builderState.getKnownVersionAliases();

            // We're looking for catalogs in the first place, because they define aliases and properties
            // that we can inline. Then, there are remaining dependencies which are non Micronaut modules
            // which do not publish catalogs. The ignored bom files list is the list of files that we can
            // safely ignore because there are catalogs.
            Set<String> ignoredBomFiles = new HashSet<>();
            var knownCatalogModules = catalogs.stream()
                .map(ResolvedArtifactResult::getVariant)
                .map(ResolvedVariantResult::getOwner)
                .filter(ModuleComponentIdentifier.class::isInstance)
                .map(ModuleComponentIdentifier.class::cast)
                .map(mci -> mci.getModuleIdentifier().toString())
                .collect(Collectors.toSet());
            List<String> extraBomsToResolve = new ArrayList<>();
            catalogs.forEach(catalogArtifact -> {
                    var catalogFile = catalogArtifact.getFile();
                    var excludes = determineExcludes(excludedInlinedAliases, catalogFile);
                    var includes = inlinedAliases.get().getOrDefault(baseNameOf(catalogFile), Set.of());
                    var excludedAliases = findRegularEntries(excludes);
                    var excludedAliasesPrefixes = findWildcardEntries(excludes);
                    var includeAliases = findRegularEntries(includes);
                    var includedAliasesPrefixes = findWildcardEntries(includes);

                    performSingleCatalogFileInclusion(
                        log,
                        inlinedPomProperties,
                        catalogFile,
                        ignoredBomFiles,
                        includeAliases,
                        includedAliasesPrefixes,
                        excludedAliases,
                        excludedAliasesPrefixes,
                        knownAliases,
                        knownVersionAliases,
                        builder,
                        knownPluginAliases,
                        inlinedMavenDependencies,
                        knownCatalogModules,
                        extraBomsToResolve);
                }
            );
            if (Boolean.TRUE.equals(inlineNestedBOMs.get())) {
                log.println("Regular BOMs (without version catalog) inlining is enabled");
                inlineRegularBoms(log, bomArtifacts, excludedInlinedAliases, inlinedAliases, inlinedPomProperties, inlinedMavenDependencies, ignoredBomFiles, knownAliases, knownVersionAliases, builder);
                if (!extraBomsToResolve.isEmpty()) {
                    log.println("Found the following BOMs to be recursively included: ");
                    extraBomsToResolve.forEach(bom -> log.println("    - " + bom));
                    var extraBoms = p.getConfigurations().getByName(EXTRA_BOMS_INLINING_CONFIGURATION_NAME);
                    extraBoms.getDependencies().addAll(extraBomsToResolve.stream()
                        .map(bom -> p.getDependencies().create(bom + "@pom"))
                        .toList());
                    inlineRegularBoms(log, extraBoms.getIncoming().getArtifacts().getArtifacts(), excludedInlinedAliases, inlinedAliases, inlinedPomProperties, inlinedMavenDependencies, ignoredBomFiles, knownAliases, knownVersionAliases,
                        builder);
                }
            }

        }
    }

    private static Set<String> determineExcludes(Provider<Map<String, Set<String>>> excludedInlinedAliases, File catalogFile) {
        var moduleExcludes = excludedInlinedAliases.get().getOrDefault(baseNameOf(catalogFile), Set.of());
        var starExcludes = excludedInlinedAliases.get().getOrDefault("*", Set.of());
        return Stream.concat(
                moduleExcludes.stream(),
                starExcludes.stream()
            )
            .collect(Collectors.toSet());
    }

    private void inlineRegularBoms(PrintWriter log,
                                   Set<ResolvedArtifactResult> bomArtifacts,
                                   Provider<Map<String, Set<String>>> excludedInlinedAliases,
                                   MapProperty<String, Set<String>> inlinedAliases,
                                   Map<String, String> inlinedPomProperties,
                                   List<InlinedDependency> inlinedMavenDependencies,
                                   Set<String> ignoredBomFiles,
                                   Map<String, VersionCatalogConverter.AliasRecord> knownAliases,
                                   Map<String, VersionCatalogConverter.AliasRecord> knownVersionAliases,
                                   VersionCatalogBuilder builder) {
        bomArtifacts.forEach(bomArtifact -> {
            var bomFile = bomArtifact.getFile();
            var excludes = determineExcludes(excludedInlinedAliases, bomFile);
            var includes = inlinedAliases.get().getOrDefault(baseNameOf(bomFile), Set.of());
            Set<String> excludedAliases = findRegularEntries(excludes);
            Set<String> excludedAliasesPrefixes = findWildcardEntries(excludes);
            Set<String> includeAliases = findRegularEntries(includes);
            Set<String> includedAliasesPrefixes = findWildcardEntries(includes);

            performNestedBomsInclusion(log, bomFile, includeAliases, includedAliasesPrefixes, excludedAliases, excludedAliasesPrefixes, ignoredBomFiles, knownAliases, knownVersionAliases, builder, inlinedMavenDependencies, inlinedPomProperties);
        });
    }

    private static @NotNull Set<String> findWildcardEntries(Set<String> excludes) {
        return excludes.stream().filter(a -> a.endsWith("*")).map(a -> a.substring(0, a.length() - 1)).collect(Collectors.toSet());
    }

    private static Set<String> findRegularEntries(Set<String> items) {
        return items.stream().filter(a -> !a.endsWith("*")).collect(Collectors.toSet());
    }

    private static String baseNameOf(File file) {
        var matcher = BASENAME_EXTRACTOR.matcher(file.getName());
        var baseName = file.getName();
        if (matcher.find()) {
            baseName = matcher.group(1);
        }
        return baseName;
    }

    private static void performSingleCatalogFileInclusion(PrintWriter log,
                                                          Map<String, String> inlinedPomProperties,
                                                          File catalogFile,
                                                          Set<String> ignoredBomFiles,
                                                          Set<String> includeAliases,
                                                          Set<String> includeAliasesPrefixes,
                                                          Set<String> excludeFromInlining,
                                                          Set<String> excludeFromInliningPrefixes,
                                                          Map<String, VersionCatalogConverter.AliasRecord> knownAliases,
                                                          Map<String, VersionCatalogConverter.AliasRecord> knownVersionAliases,
                                                          VersionCatalogBuilder builder,
                                                          Map<String, VersionCatalogConverter.AliasRecord> knownPluginAliases,
                                                          List<InlinedDependency> inlinedMavenDependencies,
                                                          Set<String> knownCatalogModules,
                                                          List<String> extraBomsToResolve) {
        String source = catalogFile.getName();
        log.println("Inlining catalog file: " + source);
        ignoredBomFiles.add(source.substring(0, source.lastIndexOf(".toml")) + ".pom");
        try (FileInputStream fis = new FileInputStream(catalogFile)) {
            LenientVersionCatalogParser parser = new LenientVersionCatalogParser();
            parser.parse(fis);
            Set<Library> librariesTable = parser.getModel().getLibrariesTable();
            Set<Plugin> pluginsTable = parser.getModel().getPluginsTable();
            Set<VersionModel> versionsTable = parser.getModel().getVersionsTable();
            performLibrariesInlining(log,
                catalogFile.getName(),
                inlinedPomProperties,
                includeAliases,
                includeAliasesPrefixes,
                excludeFromInlining,
                excludeFromInliningPrefixes,
                knownAliases,
                knownVersionAliases,
                builder,
                librariesTable,
                versionsTable,
                source,
                inlinedMavenDependencies,
                knownCatalogModules,
                extraBomsToResolve);
            performPluginsInlining(log,
                catalogFile.getName(),
                inlinedPomProperties,
                includeAliases,
                includeAliasesPrefixes,
                excludeFromInlining,
                excludeFromInliningPrefixes,
                knownVersionAliases,
                builder,
                knownPluginAliases,
                pluginsTable,
                versionsTable,
                source);
        } catch (IOException e) {
            System.err.println("Unable to parse version catalog file: " + catalogFile);
        }
    }

    private static void performLibrariesInlining(PrintWriter log,
                                                 String catalogName,
                                                 Map<String, String> inlinedPomProperties,
                                                 Set<String> includeAliases,
                                                 Set<String> includeAliasesPrefixes,
                                                 Set<String> excludeFromInlining,
                                                 Set<String> excludeFromInliningPrefixes,
                                                 Map<String, VersionCatalogConverter.AliasRecord> knownAliases,
                                                 Map<String, VersionCatalogConverter.AliasRecord> knownVersionAliases,
                                                 VersionCatalogBuilder builder,
                                                 Set<Library> librariesTable,
                                                 Set<VersionModel> versionsTable,
                                                 String source,
                                                 List<InlinedDependency> inlinedMavenDependencies,
                                                 Set<String> knownCatalogModules,
                                                 List<String> extraBomsToResolve) {
        librariesTable.forEach(library -> {
            String alias = library.getAlias();
            var includeExcludeReason = shouldInclude(alias, includeAliases, includeAliasesPrefixes, excludeFromInlining, excludeFromInliningPrefixes);
            if (includeExcludeReason.included()) {
                if (!knownAliases.containsKey(alias)) {
                    String reference = library.getVersion().getReference();
                    String version = null;
                    if (reference != null) {
                        version = reference;
                        var requiredVersion = versionsTable.stream().filter(m -> reference.equals(m.getReference())).findFirst().get().getVersion().getRequire();
                        if (requiredVersion != null) {
                            var versionProperty = toPropertyName(reference) + ".version";
                            inlinedMavenDependencies.add(new InlinedDependency(library.getGroup(), library.getName(), versionProperty));
                            maybeAddExtraBomToResolve(log, catalogName, knownCatalogModules, extraBomsToResolve, library, alias, requiredVersion);
                            if (!knownVersionAliases.containsKey(reference)) {
                                builder.version(reference, requiredVersion);
                                inlinedPomProperties.put(reference, requiredVersion);
                            } else {
                                Set<String> sources = knownVersionAliases.get(reference).getSources();
                                if (!sources.equals(Collections.singleton(source))) {
                                    log.println("    [" + catalogName + "] [Warning] While inlining " + source + ", version alias '" + alias + "' is already defined in the catalog by " + sources + " so it won't be imported");
                                }
                            }
                        }
                        knownVersionAliases.get(reference).addSource(source);
                    }
                    VersionCatalogBuilder.LibraryAliasBuilder libraryBuilder = builder.library(alias, library.getGroup(), library.getName());
                    if (version != null) {
                        log.println("    [" + catalogName + "] Inlining '" + alias + "' with version '" + version + "' because " + includeExcludeReason.reason());
                        libraryBuilder.versionRef(reference);
                    } else {
                        log.println("    [" + catalogName + "] Inlining '" + alias + "' without version because " + includeExcludeReason.reason());
                        libraryBuilder.withoutVersion();
                    }
                } else {
                    maybeWarn(knownAliases, alias, source);
                }
                knownAliases.get(alias).addSource(source);
            } else {
                log.println("    [" + catalogName + "] Excluding '" + alias + "' from inlining because " + includeExcludeReason.reason());
            }
        });
    }

    private static void maybeAddExtraBomToResolve(PrintWriter log, String context, Set<String> knownCatalogModules, List<String> extraBomsToResolve, Library library, String alias, String requiredVersion) {
        if (alias.startsWith("boms-")) {
            var module = library.getModule();
            if (!knownCatalogModules.contains(module)) {
                var gav = module + ":" + requiredVersion;
                log.println("    [" + context + "] Found extra BOM to inline: " + gav);
                extraBomsToResolve.add(gav);
            }
        }
    }

    private static void performPluginsInlining(PrintWriter log,
                                               String catalogName,
                                               Map<String, String> inlinedPomProperties,
                                               Set<String> includeAliases,
                                               Set<String> includeAliasesPrefixes,
                                               Set<String> excludeFromInlining,
                                               Set<String> excludeFromInliningPrefixes,
                                               Map<String, VersionCatalogConverter.AliasRecord> knownVersionAliases,
                                               VersionCatalogBuilder builder,
                                               Map<String, VersionCatalogConverter.AliasRecord> knownPluginAliases,
                                               Set<Plugin> pluginsTable,
                                               Set<VersionModel> versionsTable,
                                               String source) {
        pluginsTable.forEach(plugin -> {
            String alias = plugin.alias();
            var includeExcludeReason = shouldInclude(alias, includeAliases, includeAliasesPrefixes, excludeFromInlining, excludeFromInliningPrefixes);
            if (includeExcludeReason.included()) {
                if (!knownPluginAliases.containsKey(alias)) {
                    String reference = plugin.version().getReference();
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
                                log.println("    [" + catalogName + "] [Warning] While inlining plugin " + source + ", version alias '" + alias + "' is already defined in the catalog by " + sources + " so it won't be imported");
                            }
                        }
                        knownVersionAliases.get(reference).addSource(source);
                    }
                    VersionCatalogBuilder.PluginAliasBuilder pluginAliasBuilder = builder.plugin(alias, plugin.id());
                    if (version != null) {
                        pluginAliasBuilder.versionRef(reference);
                    } else {
                        pluginAliasBuilder.version(plugin.version().getVersion().getRequire());
                    }
                } else {
                    maybeWarn(knownPluginAliases, alias, source);
                }
                knownPluginAliases.get(alias).addSource(source);
            } else {
                log.println("    [" + catalogName + "] Excluding plugin '" + alias + "' from inlining because " + includeExcludeReason.reason());
            }
        });
    }

    private void performNestedBomsInclusion(PrintWriter log,
                                            File bomFile,
                                            Set<String> includeAliases,
                                            Set<String> includeAliasesPrefixes,
                                            Set<String> excludeFromInlining,
                                            Set<String> excludeFromInliningPrefixes,
                                            Set<String> ignoredBomFiles,
                                            Map<String, VersionCatalogConverter.AliasRecord> knownAliases,
                                            Map<String, VersionCatalogConverter.AliasRecord> knownVersionAliases,
                                            VersionCatalogBuilder builder,
                                            List<InlinedDependency> inlinedMavenDependencies,
                                            Map<String, String> inlinedPomProperties) {

        var bomFileName = bomFile.getName();
        if (!ignoredBomFiles.contains(bomFileName)) {
            log.println("Inlining external BOM: " + bomFileName);
            var request = new DefaultModelBuildingRequest();
            request.setProcessPlugins(false);
            request.setPomFile(bomFile);
            request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
            request.setModelResolver(mavenModelResolver);
            try {
                var knownAliasesSnakeCase = knownAliases.keySet()
                    .stream()
                    .map(MicronautBomPlugin::convertToAlias)
                    .collect(Collectors.toSet());
                var knownVersionAliasesSnakeCase = knownVersionAliases.keySet()
                    .stream()
                    .map(MicronautBomPlugin::convertToAlias)
                    .collect(Collectors.toSet());
                var modelBuilder = createMavenModelBuilder();
                var model = modelBuilder.build(request).getEffectiveModel();
                model.getDependencyManagement()
                    .getDependencies()
                    .forEach(dep -> {
                        var alias = convertToAlias(dep.getArtifactId());
                        var includeExcludeReason = shouldInclude(alias, includeAliases, includeAliasesPrefixes, excludeFromInlining, excludeFromInliningPrefixes);
                        if (includeExcludeReason.included()) {
                            if (knownAliasesSnakeCase.contains(alias) || knownAliases.containsKey(alias)) {
                                maybeWarn(knownAliases, alias, bomFileName);
                            } else {
                                if (knownVersionAliasesSnakeCase.contains(alias)) {
                                    maybeWarn(knownVersionAliases, alias, bomFileName);
                                } else {
                                    builder.library(alias, dep.getGroupId(), dep.getArtifactId())
                                        .versionRef(alias);
                                    builder.version(alias, dep.getVersion());
                                    knownAliases.get(alias).addSource(bomFileName);
                                    knownVersionAliases.get(alias).addSource(bomFileName);
                                    var versionProperty = toPropertyName(alias) + ".version";
                                    inlinedMavenDependencies.add(new InlinedDependency(dep.getGroupId(), dep.getArtifactId(), versionProperty));
                                    inlinedPomProperties.putIfAbsent(toPropertyName(alias), dep.getVersion());
                                    log.println("    [" + bomFileName + "] Inlining " + alias + " because " + includeExcludeReason.reason());
                                }
                            }
                        } else {
                            log.println("    [" + bomFileName + "] Excluding " + alias + " from regular BOM inlining because " + includeExcludeReason.reason());
                        }
                    });
            } catch (ModelBuildingException e) {
                log.println("Unable to inline POM file " + bomFile + ": " + e.getMessage());
            }
        } else {
            log.println("Ignoring BOM file: " + bomFileName + " because we've already found a catalog for it");
        }
    }

    private static IncludeExcludeReason shouldInclude(String alias, Set<String> included, Set<String> includedPrefixes, Set<String> excluded, Set<String> excludePrefixes) {
        if (included.isEmpty() && excluded.isEmpty() && includedPrefixes.isEmpty() && excludePrefixes.isEmpty()) {
            return new IncludeExcludeReason(true, "no include or exclude pattern provided");
        }
        if (included.isEmpty() && includedPrefixes.isEmpty()) {
            if (excluded.contains(alias)) {
                return new IncludeExcludeReason(false, "alias is explicitly excluded.");
            }
            for (String prefix : excludePrefixes) {
                if (alias.startsWith(prefix)) {
                    return new IncludeExcludeReason(false, "alias matches excluded prefix: " + prefix);
                }
            }
            return new IncludeExcludeReason(true, "alias is not explicitly excluded and does not match any excluded prefix.");
        }

        if (included.contains(alias)) {
            if (excluded.contains(alias)) {
                return new IncludeExcludeReason(false, "alias is explicitly included but also explicitly excluded.");
            }
            for (String prefix : excludePrefixes) {
                if (alias.startsWith(prefix)) {
                    return new IncludeExcludeReason(false, "alias is explicitly included but matches excluded prefix: " + prefix);
                }
            }
            return new IncludeExcludeReason(true, "alias is explicitly included.");
        }

        for (String prefix : includedPrefixes) {
            if (alias.startsWith(prefix)) {
                if (excluded.contains(alias)) {
                    return new IncludeExcludeReason(false, "alias matches included prefix: " + prefix + " but is also explicitly excluded.");
                }
                for (String excludePrefix : excludePrefixes) {
                    if (alias.startsWith(excludePrefix)) {
                        return new IncludeExcludeReason(false, "alias matches included prefix: " + prefix + " but also matches excluded prefix: " + excludePrefix);
                    }
                }
                return new IncludeExcludeReason(true, "alias matches included prefix: " + prefix);
            }
        }

        if (excluded.contains(alias)) {
            return new IncludeExcludeReason(false, "alias is explicitly excluded.");
        }
        for (String prefix : excludePrefixes) {
            if (alias.startsWith(prefix)) {
                return new IncludeExcludeReason(false, "alias matches excluded prefix: " + prefix);
            }
        }

        return new IncludeExcludeReason(false, "alias is not included and does not match any included prefix.");
    }


    public static String convertToAlias(String artifactId) {
        return artifactId.replaceAll("[^a-zA-Z0-9-]", "-")
            .replaceAll("([a-z])([A-Z]+)", "$1-$2")
            .toLowerCase();
    }

    private ModelBuilder createMavenModelBuilder() {
        var modelProcessor = new DefaultModelProcessor();
        var reader = new DefaultModelReader();
        var locator = new DefaultModelLocator();
        var modelInterpolator = new StringVisitorModelInterpolator();
        var versionProcessor = new DefaultModelVersionProcessor();
        var modelNormalizer = new DefaultModelNormalizer();
        var modelValidator = new DefaultModelValidator(versionProcessor);
        var profileSelector = new DefaultProfileSelector();
        var superPomProvider = new DefaultSuperPomProvider();
        var inheritanceAssembler = new DefaultInheritanceAssembler();
        var pathTranslator = new DefaultPathTranslator();
        var urlNormalizer = new DefaultUrlNormalizer();
        var modelUrlNormalizer = new DefaultModelUrlNormalizer();
        modelUrlNormalizer.setUrlNormalizer(urlNormalizer);
        modelInterpolator.setVersionPropertiesProcessor(versionProcessor);
        modelInterpolator.setPathTranslator(pathTranslator);
        modelInterpolator.setUrlNormalizer(urlNormalizer);
        modelProcessor.setModelReader(reader);
        modelProcessor.setModelLocator(locator);
        superPomProvider.setModelProcessor(modelProcessor);
        var modelBuilder = new DefaultModelBuilder();
        modelBuilder.setModelProcessor(modelProcessor);
        modelBuilder.setModelInterpolator(modelInterpolator);
        modelBuilder.setModelNormalizer(modelNormalizer);
        modelBuilder.setModelValidator(modelValidator);
        modelBuilder.setProfileSelector(profileSelector);
        modelBuilder.setSuperPomProvider(superPomProvider);
        modelBuilder.setInheritanceAssembler(inheritanceAssembler);
        modelBuilder.setModelUrlNormalizer(modelUrlNormalizer);
        var modelPathTranslator = new DefaultModelPathTranslator();
        modelPathTranslator.setPathTranslator(pathTranslator);
        modelBuilder.setModelPathTranslator(modelPathTranslator);
        var profileActivationFilePathInterpolator = new ProfileActivationFilePathInterpolator();
        profileActivationFilePathInterpolator.setPathTranslator(pathTranslator);
        modelBuilder.setProfileActivationFilePathInterpolator(profileActivationFilePathInterpolator);
        var depMgmtImporter = new DefaultDependencyManagementImporter();
        modelBuilder.setDependencyManagementImporter(depMgmtImporter);
        var depMgmtInjector = new DefaultDependencyManagementInjector();
        modelBuilder.setDependencyManagementInjector(depMgmtInjector);
        var reportingConverter = new DefaultReportingConverter();
        modelBuilder.setReportingConverter(reportingConverter);
        var pluginManagementInjector = new DefaultPluginManagementInjector();
        modelBuilder.setPluginManagementInjector(pluginManagementInjector);
        return modelBuilder;
    }

    private static void maybeWarn(Map<String, VersionCatalogConverter.AliasRecord> knownPluginAliases, String alias, String source) {
        VersionCatalogConverter.AliasRecord record = knownPluginAliases.get(alias);
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
        return Optional.ofNullable(versionCatalogsExtension).flatMap(e -> e.find(bomExtension.getCatalogName().get()));
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

    private record InlinedDependency(
        String groupId,
        String artifactId,
        String versionProperty
    ) {

    }

    private record IncludeExcludeReason(
        boolean included,
        String reason
    ) {

    }
}
