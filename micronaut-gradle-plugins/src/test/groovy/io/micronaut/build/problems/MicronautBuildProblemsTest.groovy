package io.micronaut.build.problems

import spock.lang.Specification

class MicronautBuildProblemsTest extends Specification {

    void "sanitizes sensitive diagnostic values"() {
        expect:
        MicronautBuildProblems.sanitizeDiagnosticText('password=secret token:abc Authorization: Bearer abc.def username=admin') ==
                'password=<redacted> token:<redacted> Authorization: <redacted> username=<redacted>'
    }

    void "sanitizes sensitive json diagnostic values"() {
        when:
        def sanitized = MicronautBuildProblems.sanitizeDiagnosticText('{"token":"abc123","password":"secret","Authorization":"Bearer abc.def","username":"admin"}')

        then:
        !sanitized.contains('abc123')
        !sanitized.contains('secret')
        !sanitized.contains('abc.def')
        !sanitized.contains('admin')
        sanitized == '{"token":"<redacted>","password":"<redacted>","Authorization":"<redacted>","username":"<redacted>"}'
    }

    void "sanitizes sensitive quoted json diagnostic values with spaces and commas"() {
        when:
        def sanitized = MicronautBuildProblems.sanitizeDiagnosticText('{"password":"secret value","token":"abc,def","Authorization":"Bearer abc def","username":"admin user"}')

        then:
        !sanitized.contains('secret value')
        !sanitized.contains('abc,def')
        !sanitized.contains('abc def')
        !sanitized.contains('admin user')
        sanitized == '{"password":"<redacted>","token":"<redacted>","Authorization":"<redacted>","username":"<redacted>"}'
    }

    void "bounds long diagnostic values"() {
        when:
        def sanitized = MicronautBuildProblems.sanitizeDiagnosticText('a' * 1_100)

        then:
        sanitized.length() < 1_100
        sanitized.endsWith('... (truncated)')
    }

    void "bounds diagnostic values before sanitizing"() {
        given:
        def diagnostic = '{"password":"' + ('secret value ' * 200) + '","message":"' + ('a' * 5_000) + '"}'

        when:
        def sanitized = MicronautBuildProblems.sanitizeDiagnosticText(diagnostic)

        then:
        sanitized.length() <= 1_000
        sanitized.endsWith('... (truncated)')
        !sanitized.contains('secret value')
    }
}
