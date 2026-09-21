package io.micronaut.build.utils

import spock.lang.Specification

class ProviderUtilsSpec extends Specification {

    void "detects CI from #description"(Map<String, String> environment, boolean expected, String description) {
        expect:
        ProviderUtils.guessCI(environment::get) == expected

        where:
        environment                                                    | expected | description
        [CI: 'true', GRADLE_ENTERPRISE_ACCESS_KEY: 'ge.micronaut.io=x'] | true     | 'the Gradle Enterprise access key'
        [CI: 'true', DEVELOCITY_ACCESS_KEY: 'ge.micronaut.io=x']        | true     | 'the Develocity access key'
        [CI: 'true', DEVELOCITY_ACCESS_KEY: '']                        | false    | 'an empty access key, as for fork pull requests'
        [CI: 'true']                                                   | false    | 'no access key'
        [DEVELOCITY_ACCESS_KEY: 'ge.micronaut.io=x']                   | false    | 'an access key outside CI'
    }

    void "writes to the build cache by default only for pushes to trusted branches: #eventName #ref"(String eventName, String ref, boolean expected) {
        expect:
        ProviderUtils.isTrustedGitHubPush([GITHUB_EVENT_NAME: eventName, GITHUB_REF: ref]::get) == expected

        where:
        eventName             | ref                          | expected
        'push'                | 'refs/heads/5.2.x'           | true
        'push'                | 'refs/heads/master'          | true
        'push'                | 'refs/heads/main'            | true
        'push'                | 'refs/heads/feature/5.2.x'   | false
        'push'                | 'refs/heads/claude/fix'      | false
        'push'                | 'refs/tags/v5.2.0'           | false
        'pull_request'        | 'refs/pull/1/merge'          | false
        'pull_request_target' | 'refs/heads/5.2.x'           | false
        'merge_group'         | 'refs/heads/gh-readonly-queue/5.2.x/pr-1' | false
        'workflow_run'        | 'refs/heads/5.2.x'           | false
        'schedule'            | 'refs/heads/5.2.x'           | false
        'workflow_dispatch'   | 'refs/heads/5.2.x'           | false
        null                  | null                         | false
    }

    void "finds the access key from #description"(Map<String, String> environment, Map<String, String> systemProperties, String expected, String description) {
        expect:
        ProviderUtils.findDevelocityAccessKey(environment::get, systemProperties::get) == expected

        where:
        environment                                                                      | systemProperties                          | expected                | description
        [DEVELOCITY_ACCESS_KEY: 'ge.micronaut.io=new']                                    | [:]                                       | 'ge.micronaut.io=new'   | 'the Develocity variable'
        [GRADLE_ENTERPRISE_ACCESS_KEY: 'ge.micronaut.io=old']                             | [:]                                       | 'ge.micronaut.io=old'   | 'the Gradle Enterprise variable'
        [GRADLE_ENTERPRISE_ACCESS_KEY: '', DEVELOCITY_ACCESS_KEY: 'ge.micronaut.io=new']   | [:]                                       | 'ge.micronaut.io=new'   | 'the Develocity variable when the legacy one is blank'
        [DEVELOCITY_ACCESS_KEY: ' ']                                                      | [GRADLE_ENTERPRISE_ACCESS_KEY: 'ge.micronaut.io=prop'] | 'ge.micronaut.io=prop' | 'the legacy system property'
        [DEVELOCITY_ACCESS_KEY: '']                                                       | [:]                                       | null                    | 'nothing when all are blank'
    }
}
