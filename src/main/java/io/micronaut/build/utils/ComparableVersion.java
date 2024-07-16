package io.micronaut.build.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A version model, used for comparing versions when performing automatic updates.
 * The version model is primarily aimed at supporting semantic versioning, but in
 * order to take into account more real world use cases, it also has support for
 * qualifiers and "extra" version components.
 */
public final class ComparableVersion implements Comparable<ComparableVersion> {
    private static final List<String> PARTIAL_QUALIFIER_ORDER = List.of("snapshot", "alpha", "beta", "rc", "final");

    private final String fullVersion;
    private final Integer major;
    private final Integer minor;
    private final Integer patch;
    private final String qualifier;
    private final Integer qualifierVersion;
    private final List<Integer> extraVersions;

    ComparableVersion(
        String fullVersion,
        Integer major,
        Integer minor,
        Integer patch,
        String qualifier,
        Integer qualifierVersion, List<Integer> extraVersions
    ) {
        this.fullVersion = fullVersion;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier;
        this.qualifierVersion = qualifierVersion;
        this.extraVersions = extraVersions;
    }

    public String fullVersion() {
        return fullVersion;
    }

    public Optional<Integer> major() {
        return Optional.ofNullable(major);
    }

    public Optional<Integer> minor() {
        return Optional.ofNullable(minor);
    }

    public Optional<Integer> patch() {
        return Optional.ofNullable(patch);
    }

    public Optional<String> qualifier() {
        return Optional.ofNullable(qualifier);
    }

    public Optional<Integer> qualifierVersion() {
        return Optional.ofNullable(qualifierVersion);
    }

    public List<Integer> getExtraVersions() {
        return extraVersions;
    }

    public List<Integer> getVersionComponents() {
        var all = new ArrayList<Integer>(3 + extraVersions.size());
        all.add(Objects.requireNonNullElse(major, 0));
        all.add(Objects.requireNonNullElse(minor, 0));
        all.add(Objects.requireNonNullElse(patch, 0));
        all.addAll(extraVersions);
        return all;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ComparableVersion that = (ComparableVersion) o;
        return Objects.equals(fullVersion, that.fullVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fullVersion);
    }

    @Override
    public String toString() {
        return fullVersion;
    }

    @Override
    public int compareTo(ComparableVersion o) {
        var components = getVersionComponents();
        var otherComponents = o.getVersionComponents();

        if (components.size() != otherComponents.size()) {
            if (components.size() < otherComponents.size()) {
                components = new ArrayList<>(components);
                while (components.size() < otherComponents.size()) {
                    components.add(0);
                }
            } else {
                otherComponents = new ArrayList<>(otherComponents);
                while (otherComponents.size() < components.size()) {
                    otherComponents.add(0);
                }
            }
        }

        for (int i = 0; i < components.size(); i++) {
            int result = components.get(i).compareTo(otherComponents.get(i));
            if (result != 0) {
                return result;
            }
        }

        if (qualifier == null && o.qualifier != null) {
            return 1;
        } else if (qualifier != null && o.qualifier == null) {
            return -1;
        } else if (qualifier != null) {
            int thisQualifierIndex = PARTIAL_QUALIFIER_ORDER.indexOf(qualifier.toLowerCase(Locale.US));
            int otherQualifierIndex = PARTIAL_QUALIFIER_ORDER.indexOf(o.qualifier.toLowerCase(Locale.US));

            if (thisQualifierIndex != otherQualifierIndex) {
                return Integer.compare(thisQualifierIndex, otherQualifierIndex);
            }
        }

        if (qualifierVersion != null && o.qualifierVersion != null) {
            return qualifierVersion.compareTo(o.qualifierVersion);
        } else if (qualifierVersion != null) {
            return 1;
        } else if (o.qualifierVersion != null) {
            return -1;
        }

        return 0;
    }

}
