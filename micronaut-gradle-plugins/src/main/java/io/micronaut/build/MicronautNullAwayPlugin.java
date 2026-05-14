package io.micronaut.build;

import io.micronaut.build.nullaway.MicronautNullAwayExtension;
import io.micronaut.build.utils.DefaultVersions;
import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptionsKt;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Collections;
import java.util.Locale;

import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault;

public class MicronautNullAwayPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("io.micronaut.build.internal.base");
        project.getPluginManager().apply("net.ltgt.errorprone");

        MicronautNullAwayExtension extension = project.getExtensions().create("micronautNullAway", MicronautNullAwayExtension.class);

        project.getDependencies().addProvider("errorprone",
            versionProviderOrDefault(project, "nullaway", DefaultVersions.NULLAWAY_VERSION)
                .map(version -> "com.uber.nullaway:nullaway:" + version));
        project.getDependencies().addProvider("errorprone",
            versionProviderOrDefault(project, "error_prone_core", DefaultVersions.ERROR_PRONE_CORE_VERSION)
                .map(version -> "com.google.errorprone:error_prone_core:" + version));

        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            ErrorProneOptionsKt.errorprone(compile.getOptions(), options -> {
                extension.getChecks().getOrElse(Collections.<String, CheckSeverity>emptyMap()).forEach(options::check);
                extension.getOptions().getOrElse(Collections.<String, String>emptyMap()).forEach(options::option);
                extension.getAdditionalActions().forEach(action -> action.execute(options));
            });
            boolean disableForTask = extension.getDisableOnTaskNameContains().getOrElse(Collections.emptySet()).stream()
                .anyMatch(needle -> compile.getName().toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US)));
            boolean disableForProject = extension.getDisableOnProjectNameContains().getOrElse(Collections.emptySet()).stream()
                .anyMatch(needle -> project.getName().toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US)));
            if (disableForTask || disableForProject) {
                ErrorProneOptionsKt.errorprone(compile.getOptions(), options -> {
                    extension.getDisabledChecks().getOrElse(Collections.emptySet()).forEach(options::disable);
                });
            }
        });
    }
}
