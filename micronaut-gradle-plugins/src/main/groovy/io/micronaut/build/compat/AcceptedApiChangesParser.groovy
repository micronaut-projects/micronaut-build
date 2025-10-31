package io.micronaut.build.compat

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

@CompileStatic
class AcceptedApiChangesParser {
    static List<AcceptedApiChange> parse(InputStream jsonStream) {
        def parser = new JsonSlurper()
        List<Map<String, String>> json = parser.parse(jsonStream) as List<Map<String, String>>
        return json.collect {map ->
            new AcceptedApiChange(
                    map["type"],
                    map["member"],
                    map["reason"]
            )
        }
    }
}
