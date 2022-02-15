package io.micronaut.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.testing.base.TestingExtension;
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

            final TestingExtension testing = project.getExtensions().findByType(TestingExtension.class);
            if (testing != null) {
                testing.getSuites().withType(JvmTestSuite.class, JvmTestSuite::useJUnitJupiter);
            }
        });
    }
}
