package io.micronaut.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;

@SuppressWarnings("UnstableApiUsage")
public class MicronautQualityChecksParticipantPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        configureJacoco(project);
    }

    private void configureJacoco(final Project project) {
        project.getPluginManager().withPlugin("java", p -> {
            project.getPluginManager().apply(JacocoPlugin.class);
        });
    }
}
