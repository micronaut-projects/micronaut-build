package io.micronaut.docs

import spock.lang.Specification
import spock.lang.Unroll

class ApiMacroSpec extends Specification {

    @Unroll
    void "#target is expected as #expected"(String target, String expected) {
        when:
        String result = ApiMacro.scapeDots(target)

        then:
        expected == result

        where:
        target                                                                                                     || expected
        'io.micronaut.security.config.RedirectConfigurationProperties.ForbiddenRedirectConfigurationProperties'    || 'io/micronaut/security/config/RedirectConfigurationProperties.ForbiddenRedirectConfigurationProperties'
        'io.micronaut.security.endpoints.introspection.IntrospectionConfigurationProperties'                       || 'io/micronaut/security/endpoints/introspection/IntrospectionConfigurationProperties'
    }
}
