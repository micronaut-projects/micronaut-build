package io.micronaut.build.quality

import io.micronaut.build.AbstractFunctionalTest

class PreReleaseFunctionalTest extends AbstractFunctionalTest {

    void "can run a pre-release check"() {
        given:
        withSample("test-micronaut-module")
        file("subproject1/build.gradle") << """
            dependencies {
                // We allows snapshot dependencies during development, but not for releasing
                implementation "io.micronaut.validation:micronaut-validation:666-SNAPSHOT"
            }
        """

        when:
        run 'checkPom'

        then: "snapshots are allowed during development"
        tasks {
            succeeded ':subproject1:checkPom'
            succeeded ':subproject2:checkPom'
        }

        when:
        fails 'preReleaseCheck', '--continue'

        then: "pre-release check captures snapshot dependencies"
        tasks {
            failed ':subproject1:checkPom'
            succeeded ':subproject2:checkPom'
        }
        outputContains "POM io.micronaut.project-template:micronaut-subproject1:1.0.0-SNAPSHOT (via io.micronaut.project-template:micronaut-subproject1:1.0.0-SNAPSHOT) declares a SNAPSHOT dependency on io.micronaut.validation:micronaut-validation:666-SNAPSHOT"
        errorOutputContains "POM verification failed"

        when:
        run 'preReleaseCheck', '-Dmicronaut.fail.on.snapshots=false', '--continue'

        then: "pre-release check can be configured to ignore snapshot dependencies"
        tasks {
            succeeded ':subproject1:checkPom'
            succeeded ':subproject2:checkPom'
        }
    }
}
