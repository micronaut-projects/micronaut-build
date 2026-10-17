package io.micronaut.build

import io.micronaut.build.problems.MicronautBuildProblems
import spock.lang.Specification

class MavenCentralPublishTaskTest extends Specification {

    void "accepts deployment id response body"() {
        expect:
        MavenCentralPublishTask.extractDeploymentId("  5f3c1c67-0ec6-4a4b-8246-064f1f61b287  ") == "5f3c1c67-0ec6-4a4b-8246-064f1f61b287"
    }

    void "encodes deployment id in status URL"() {
        expect:
        MavenCentralPublishTask.buildStatusUrl("deployment:abc") == "https://central.sonatype.com/api/v1/publisher/status?id=deployment%3Aabc"
    }

    void "rejects credential json upload response as deployment id"() {
        given:
        def responseBody = '{"token":"abc123","password":"secret","Authorization":"Bearer abc.def","username":"admin"}'

        when:
        def deploymentId = MavenCentralPublishTask.extractDeploymentId(responseBody)
        def sanitizedBody = MicronautBuildProblems.sanitizeDiagnosticText(responseBody)

        then:
        deploymentId == null
        !sanitizedBody.contains('abc123')
        !sanitizedBody.contains('secret')
        !sanitizedBody.contains('abc.def')
        !sanitizedBody.contains('admin')
    }

    void "rejects credential-like deployment id response body"() {
        expect:
        MavenCentralPublishTask.extractDeploymentId(responseBody) == null

        where:
        responseBody << [
            'token:abc123',
            'password:secret'
        ]
    }
}
