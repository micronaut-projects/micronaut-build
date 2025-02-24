package io.micronaut.build;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import io.micronaut.build.pom.BomSuppressions;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class MicronautBuildExtension {

  public static final String DEFAULT_DEPENDENCY_UPDATES_PATTERN = "(?i).+(-|\\.?)(b|M|RC|Dev)\\d?.*";
  public static final int DEFAULT_JAVA_VERSION = 17;
  public static final String DEFAULT_CHECKSTYLE_VERSION = "10.5.0";

  private final BuildEnvironment environment;

  private final MicronautCompileOptions compileOptions;

  private Closure<?> resolutionStrategy;

  @Inject
  public abstract ObjectFactory getObjects();

  @Inject
  public MicronautBuildExtension(final BuildEnvironment buildEnvironment) {
    this.environment = buildEnvironment;
    this.compileOptions = getObjects().newInstance(MicronautCompileOptions.class);

    getJavaVersion().convention(DEFAULT_JAVA_VERSION);
    getTestJavaVersion().convention(Integer.valueOf(JavaVersion.current().getMajorVersion()));
    getCheckstyleVersion().convention(DEFAULT_CHECKSTYLE_VERSION);
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
   * The version of Java this project is supporting. This is equivalent
   * to setting the Java language version on the Java extension.
   * @return the java version for this project
   */
  public abstract Property<Integer> getJavaVersion();

  /**
   * The version of Java for running the tests. Defaults to JavaVersion.current().
   * @return the java version for this project
   */
  public abstract Property<Integer> getTestJavaVersion();

  /**
   * The default source compatibility
   * @deprecated prefer using {@link #getJavaVersion()} instead
   */
  @Deprecated
  public abstract Property<String> getSourceCompatibility();

  /**
   * The default target compatibility
   * @deprecated prefer using {@link #getJavaVersion()} instead
   */
  @Deprecated
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

  /**
   * Errors which should be suppressed when validating POMs.
   */
  @Nested
  public abstract BomSuppressions getBomSuppressions();

  /**
   * The test framework to use.
   */
  public abstract Property<TestFramework> getTestFramework();

  void bomSuppressions(Action<? super BomSuppressions> spec) {
    spec.execute(getBomSuppressions());
  }

  public void resolutionStrategy(@DelegatesTo(ResolutionStrategy.class) Closure<?> closure) {
    this.resolutionStrategy = closure;
  }
}
