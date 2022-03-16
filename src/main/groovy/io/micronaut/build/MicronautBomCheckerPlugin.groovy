package io.micronaut.build

import org.gradle.api.Plugin
import org.gradle.api.Project
/**
 * This plugin verifies the BOMs which are used in the project
 * version catalog.
 * @deprecated features provided by this plugin are now part of {@link MicronautBomPlugin}
 */
@Deprecated
abstract class MicronautBomCheckerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.getLogger().warn("This plugin is deprecated and will be removed in a future version. It has been turned into a no-op.")
    }
}
