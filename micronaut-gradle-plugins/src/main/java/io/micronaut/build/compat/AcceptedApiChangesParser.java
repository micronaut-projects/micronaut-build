package io.micronaut.build.compat;

import groovy.json.JsonSlurper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

final class AcceptedApiChangesParser {
    private AcceptedApiChangesParser() {
    }

    static List<AcceptedApiChange> parse(InputStream jsonStream) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> json = (List<Map<String, String>>) new JsonSlurper().parse(jsonStream);
        return json.stream()
            .map(map -> new AcceptedApiChange(
                map.get("type"),
                map.get("member"),
                map.get("reason")
            ))
            .toList();
    }
}
