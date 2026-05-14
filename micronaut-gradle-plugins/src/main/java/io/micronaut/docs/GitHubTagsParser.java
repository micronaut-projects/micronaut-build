package io.micronaut.docs;

import groovy.json.JsonSlurper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class GitHubTagsParser {
    private GitHubTagsParser() {
    }

    public static List<SoftwareVersion> toVersions(String json) {
        if (json == null) {
            return Collections.emptyList();
        }

        Object parsed = new JsonSlurper().parseText(json);
        if (!(parsed instanceof Iterable<?> tags)) {
            return Collections.emptyList();
        }

        List<SoftwareVersion> versions = new ArrayList<>();
        for (Object tag : tags) {
            if (tag instanceof Map<?, ?> tagProperties) {
                Object name = tagProperties.get("name");
                if (name instanceof String tagName && tagName.startsWith("v")) {
                    SoftwareVersion version = SoftwareVersion.build(tagName.substring(1));
                    if (version != null) {
                        versions.add(version);
                    }
                }
            }
        }

        versions.sort(Collections.reverseOrder());
        return versions;
    }
}
