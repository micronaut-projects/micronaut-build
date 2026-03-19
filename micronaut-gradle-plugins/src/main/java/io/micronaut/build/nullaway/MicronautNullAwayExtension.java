package io.micronaut.build.nullaway;

import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.Action;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.SetProperty;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exposes NullAway configuration knobs while providing Micronaut defaults.
 */
public abstract class MicronautNullAwayExtension {

    private final List<Action<? super ErrorProneOptions>> additionalActions = new ArrayList<>();

    @Inject
    public MicronautNullAwayExtension() {
        getChecks().put("NullAway", CheckSeverity.ERROR);
        getChecks().put("MissingOverride", CheckSeverity.ERROR);
        getOptions().put("NullAway:AnnotatedPackages", "io.micronaut");
        getDisableOnTaskNameContains().add("test");
        getDisableOnProjectNameContains().add("tck");
        getDisabledChecks().add("NullAway");
    }

    public abstract MapProperty<String, CheckSeverity> getChecks();

    public abstract MapProperty<String, String> getOptions();

    public abstract SetProperty<String> getDisableOnTaskNameContains();

    public abstract SetProperty<String> getDisableOnProjectNameContains();

    public abstract SetProperty<String> getDisabledChecks();

    public List<Action<? super ErrorProneOptions>> getAdditionalActions() {
        return Collections.unmodifiableList(additionalActions);
    }

    /**
     * Adds additional configuration applied after defaults.
     *
     * @param action additional configuration action
     */
    public void configure(Action<? super ErrorProneOptions> action) {
        additionalActions.add(action);
    }
}
