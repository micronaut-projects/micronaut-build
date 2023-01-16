package io.micronaut.build.utils

import spock.lang.Specification;

class BasicAuthUtilsSpec extends Specification {

    void "BasicAuthUtils::"(String username, String password, String expected) {
        expect:
        expected == BasicAuthUtils.basicAuth(username, password)

        where:
        username   | password     || expected
        'sherlock' | 'elementary' || 'Basic c2hlcmxvY2s6ZWxlbWVudGFyeQ=='
    }
}
