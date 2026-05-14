package io.micronaut.build.pom;

import groovy.json.JsonSlurper;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PomFileAdapter {
    private PomFileAdapter() {
    }

    static PomValidation parseFromFile(File path) {
        Map<?, ?> json = asMap(new JsonSlurper().parse(path));
        Map<?, ?> pom = asMap(json.get("pomFile"));
        PomFile pomFile = new PomFile(
            string(pom.get("groupId")),
            string(pom.get("artifactId")),
            string(pom.get("version")),
            Boolean.TRUE.equals(pom.get("bom")),
            asList(pom.get("dependencies")).stream()
                .map(PomFileAdapter::toDependency)
                .toList(),
            stringMap(pom.get("properties"))
        );
        return new PomValidation(
            string(json.get("dependencyPath")),
            pomFile,
            stringMap(json.get("validDependencies")),
            stringSet(json.get("invalidDependencies"))
        );
    }

    static PomDependency toDependency(Object json) {
        Map<?, ?> dependency = asMap(json);
        return new PomDependency(
            Boolean.TRUE.equals(dependency.get("managed")),
            string(dependency.get("groupId")),
            string(dependency.get("artifactId")),
            string(dependency.get("version")),
            string(dependency.get("scope"))
        );
    }

    private static Map<?, ?> asMap(Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> asList(Object value) {
        return (List<?>) value;
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        asMap(value).forEach((key, mapValue) -> result.put(string(key), string(mapValue)));
        return result;
    }

    private static Set<String> stringSet(Object value) {
        Set<String> result = new LinkedHashSet<>();
        asList(value).forEach(item -> result.add(string(item)));
        return result;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
