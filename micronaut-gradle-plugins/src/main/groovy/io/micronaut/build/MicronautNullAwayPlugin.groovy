package io.micronaut.build

import groovy.transform.CompileStatic
import io.micronaut.build.nullaway.MicronautNullAwayExtension
import io.micronaut.build.utils.DefaultVersions
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.ErrorProneOptions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

import java.util.Collections
import java.util.Locale

import static io.micronaut.build.utils.VersionHandling.versionProviderOrDefault
import static net.ltgt.gradle.errorprone.ErrorProneOptionsKt.errorprone

@CompileStatic
class MicronautNullAwayPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply('io.micronaut.build.internal.base')
        project.pluginManager.apply('net.ltgt.errorprone')

        MicronautNullAwayExtension extension = project.extensions.create('micronautNullAway', MicronautNullAwayExtension)

        project.dependencies.addProvider('errorprone',
                versionProviderOrDefault(project, 'nullaway', DefaultVersions.NULLAWAY_VERSION)
                        .map { version -> "com.uber.nullaway:nullaway:$version" })
        project.dependencies.addProvider('errorprone',
                versionProviderOrDefault(project, 'error_prone_core', DefaultVersions.ERROR_PRONE_CORE_VERSION)
                        .map { version -> "com.google.errorprone:error_prone_core:$version" })

        project.tasks.withType(JavaCompile).configureEach { JavaCompile compile ->
            errorprone(compile.options) { ErrorProneOptions options ->
                extension.checks.getOrElse(Collections.<String, CheckSeverity>emptyMap()).each { String check, CheckSeverity severity ->
                    options.check(check, severity)
                }
                extension.options.getOrElse(Collections.<String, String>emptyMap()).each { String key, String value ->
                    options.option(key, value)
                }
                extension.additionalActions.each { action -> action.execute(options) }
            }
            boolean disableForTask = extension.disableOnTaskNameContains.getOrElse(Collections.<String>emptySet()).any { String needle ->
                compile.name.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US))
            }
            boolean disableForProject = extension.disableOnProjectNameContains.getOrElse(Collections.<String>emptySet()).any { String needle ->
                project.name.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US))
            }
            if (disableForTask || disableForProject) {
                errorprone(compile.options) { ErrorProneOptions options ->
                    extension.disabledChecks.getOrElse(Collections.<String>emptySet()).each { String check ->
                        options.disable(check)
                    }
                }
            }
        }
    }
}
