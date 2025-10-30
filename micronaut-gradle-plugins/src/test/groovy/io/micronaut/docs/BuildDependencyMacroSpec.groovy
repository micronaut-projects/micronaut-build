package io.micronaut.docs

import spock.lang.Specification

class BuildDependencyMacroSpec extends Specification {

    void "implementation is used instead of compile for gradle"() {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("micronaut-function-aws-alexa", [:])

        then:
        content.contains('data-lang="gradle">implementation')

        and: 'only one gradle version is shown'
        !content.contains('data-lang="gradle-groovy">implementation')
        !content.contains('data-lang="gradle-kotlin">implementation')

        and: 'snippet is kotlin compatible. It contains parenthesis and double quotes'
        content.contains('implementation(<span class="hljs-string">"io.micronaut:micronaut-function-aws-alexa")')
    }

    void "runtime scope is mapped to runtimeOnly in Gradle and kept in Maven"() {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("micronaut-function-aws-alexa", [scope: 'runtime'])

        then:
        content.contains('data-lang="gradle">runtimeOnly')

        and: 'only one gradle version is shown'
        !content.contains('data-lang="gradle-groovy">runtimeOnly')
        !content.contains('data-lang="gradle-kotlin">runtimeOnly')

        and: 'snippet is kotlin compatible. It contains parenthesis and double quotes'
        content.contains('runtimeOnly(<span class="hljs-string">"io.micronaut:micronaut-function-aws-alexa")')

        and: 'maven snippet has correct scope'
        content.contains('&lt;scope&gt;runtime&lt;/scope&gt;')
    }

    void "version appears after the 2nd column for gradle"() {
        when:
        def content = BuildDependencyMacro.contentForTargetAndAttributes("artifactId", ["text": 'version="1.2.3"'])

        then:
        content.contains('io.micronaut:artifactId:1.2.3')
    }

    void "classifier appears after the 3rd column for gradle"() {
        when:
        def content = BuildDependencyMacro.contentForTargetAndAttributes("artifactId", ["text": 'classifier="ARCH"'])

        then:
        !content.contains('io.micronaut:artifactId:ARCH')
        content.contains('io.micronaut:artifactId::ARCH')
    }

    void "paired version and classifier appear after the 2nd and 3rd column respectively for gradle"() {
        when:
        def content = BuildDependencyMacro.contentForTargetAndAttributes("artifactId", ["text": 'version="1.2.3",classifier="ARCH"'])

        then:
        content.contains('io.micronaut:artifactId:1.2.3:ARCH')
    }
}
