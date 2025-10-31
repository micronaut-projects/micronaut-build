package io.micronaut.docs

import groovy.json.JsonSlurper

class GitHubTagsParser {
    static List<SoftwareVersion> toVersions(String json) {
        if (json == null) {
            return Collections.emptyList();
        }
        def list = new JsonSlurper().parseText(json)
        list.findAll { it.name.startsWith('v') }.collect { SoftwareVersion.build(it.name.replace('v', '')) }.sort().reverse()
    }
}
