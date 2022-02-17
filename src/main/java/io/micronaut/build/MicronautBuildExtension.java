package io.micronaut.build;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import javax.inject.Inject;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public abstract class MicronautBuildExtension {

  public static final String DEFAULT_DEPENDENCY_UPDATES_PATTERN = "(?i).+(-|\\.?)(b|M|RC|Dev)\\d?.*";

  private final BuildEnvironment environment;

  private final MicronautCompileOptions compileOptions;

  private Closure<?> resolutionStrategy;

  @Inject
  public abstract ObjectFactory getObjects();

  @Inject
  public MicronautBuildExtension(final BuildEnvironment buildEnvironment) {
    this.environment = buildEnvironment;
    this.compileOptions = getObjects().newInstance(MicronautCompileOptions.class);

    getSourceCompatibility().convention("1.8");
    getTargetCompatibility().convention("1.8");
    getCheckstyleVersion().convention("8.33");
    getDependencyUpdatesPattern().convention(DEFAULT_DEPENDENCY_UPDATES_PATTERN);
    getEnforcedPlatform().convention(false);
    getEnableProcessing().convention(false);
    getEnableBom().convention(true);
  }

  public BuildEnvironment getEnvironment() {
    return environment;
  }

  public MicronautCompileOptions getCompileOptions() {
    return compileOptions;
  }

  public Closure<?> getResolutionStrategy() {
    return resolutionStrategy;
  }

  /**
   * The default source compatibility
   */
  public abstract Property<String> getSourceCompatibility();

  /**
   * The default target compatibility
   */
  public abstract Property<String> getTargetCompatibility();

  /**
   * The checkstyle version
   */
  public abstract Property<String> getCheckstyleVersion();

  /**
   * The default dependency update pattern
   */
  public abstract Property<String> getDependencyUpdatesPattern();

  /**
   * Whether to use enforced platform when applying BOM
   */
  public abstract Property<Boolean> getEnforcedPlatform();

  /**
   * Whether to enable Micronaut annotation processing
   */
  public abstract Property<Boolean> getEnableProcessing();

  /**
   * Whether to enable the Micronaut BOM for dependency management
   */
  public abstract Property<Boolean> getEnableBom();

  public void resolutionStrategy(@DelegatesTo(ResolutionStrategy.class) Closure<?> closure) {
    this.resolutionStrategy = closure;
  }
}
