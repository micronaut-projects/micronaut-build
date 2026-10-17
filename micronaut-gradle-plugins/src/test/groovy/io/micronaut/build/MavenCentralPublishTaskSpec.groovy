package io.micronaut.build

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import static io.micronaut.build.MavenCentralPublishTask.DeploymentStatus.*
import static io.micronaut.build.MavenCentralPublishTask.PublishingType.AUTOMATIC
import static io.micronaut.build.MavenCentralPublishTask.PublishingType.USER_MANAGED

class MavenCentralPublishTaskSpec extends Specification {

    void "publishToMavenCentral writes the deployment id to the build directory by default"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.version = '1.0.0'

        when:
        project.pluginManager.apply(MicronautParentPublishingPlugin)
        def task = project.tasks.named("publishToMavenCentral", MavenCentralPublishTask).get()

        then:
        task.publishingType.get() == USER_MANAGED
        task.deploymentIdFile.get().asFile == project.layout.buildDirectory.file("central-deployment-id.txt").get().asFile
        !task.outputs.upToDateSpec.isSatisfiedBy(task)
    }

    void "deployment state #state with publishing type #type is #expected"(String state, MavenCentralPublishTask.PublishingType type, MavenCentralPublishTask.DeploymentStatus expected) {
        given:
        String body = """{"deploymentId":"28570f16-da32-4c14-bd2e-c1acc0782365","deploymentName":"bundle","deploymentState":"$state","purls":[]}"""

        expect:
        MavenCentralPublishTask.deploymentStatus(body, type) == expected

        where:
        state        | type         || expected
        'PENDING'    | USER_MANAGED || IN_PROGRESS
        'VALIDATING' | USER_MANAGED || IN_PROGRESS
        'VALIDATED'  | USER_MANAGED || VALIDATED
        'PUBLISHING' | USER_MANAGED || IN_PROGRESS
        'PUBLISHED'  | USER_MANAGED || PUBLISHED
        'FAILED'     | USER_MANAGED || FAILED
        'PENDING'    | AUTOMATIC    || IN_PROGRESS
        'VALIDATING' | AUTOMATIC    || IN_PROGRESS
        'VALIDATED'  | AUTOMATIC    || IN_PROGRESS
        'PUBLISHING' | AUTOMATIC    || IN_PROGRESS
        'PUBLISHED'  | AUTOMATIC    || PUBLISHED
        'COMPLETE'   | AUTOMATIC    || PUBLISHED
        'FAILED'     | AUTOMATIC    || FAILED
    }

    void "deployment state is parsed when the response contains whitespace"() {
        expect:
        MavenCentralPublishTask.deploymentStatus('{ "deploymentState" : "VALIDATED" }', USER_MANAGED) == VALIDATED
    }

    void "deployment is in progress when no state is present"(String body) {
        expect:
        MavenCentralPublishTask.deploymentStatus(body, USER_MANAGED) == IN_PROGRESS

        where:
        body << [null, '', '{}']
    }
}
