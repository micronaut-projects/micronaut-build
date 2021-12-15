package io.micronaut.build

import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

abstract class MicronautBuildExtension {
    private final BuildEnvironment environment
    private final MicronautCompileOptions compileOptions

    MicronautCompileOptions getCompileOptions() {
        compileOptions
    }

    @Inject
    abstract ObjectFactory getObjects()

    @Inject
    MicronautBuildExtension(BuildEnvironment buildEnvironment) {
        this.environment = buildEnvironment
        this.compileOptions = getObjects().newInstance(MicronautCompileOptions)
    }

    /**
     * The default source compatibility
     */
    String sourceCompatibility = '1.8'
    /**
     * The default target compatibility
     */
    String targetCompatibility = '1.8'

    /**
     * The checkstyle version
     */
    String checkstyleVersion = '8.33'

    /**
     * The default dependency update pattern
     */
    String dependencyUpdatesPattern = /(?i).+(-|\.?)(b|M|RC|Dev)\d?.*/

    Closure resolutionStrategy

    /**
     * Whether to use enforced platform when applying BOM
     */
    boolean enforcedPlatform = false

    /**
     * Whether to enable Micronaut annotation processing
     */
    boolean enableProcessing = false

    /**
     * Whether to enable the Micronaut BOM for dependency management
     */
    boolean enableBom = true

    void resolutionStrategy(@DelegatesTo(ResolutionStrategy) Closure closure) {
        this.resolutionStrategy = closure
    }

    BuildEnvironment getEnvironment() {
        environment
    }
}
