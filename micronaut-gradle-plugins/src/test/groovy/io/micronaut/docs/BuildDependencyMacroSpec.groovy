package io.micronaut.docs

import spock.lang.Specification
import spock.lang.Unroll

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

    void "a pyronaut snippet is rendered as a pyproject.toml dependency block"() {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("micronaut-function-aws-alexa", [:])

        then:
        content.contains('<code class="language-toml hljs" data-lang="pyronaut">[tool.pyronaut.dependencies]')
        content.contains('runtime = [\n    <span class="hljs-string">"io.micronaut:micronaut-function-aws-alexa"</span>,\n]')

        and: 'gradle and maven snippets are still rendered'
        content.contains('data-lang="gradle">implementation')
        content.contains('data-lang="maven">')
    }

    void "pyronaut version and classifier are rendered in the coordinates"() {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("artifactId", ["text": 'version="1.2.3",classifier="ARCH"'])

        then:
        content.contains('runtime = [\n    <span class="hljs-string">"io.micronaut:artifactId:1.2.3:ARCH"</span>,')
    }

    @Unroll
    void "scope #scope is mapped to pyronaut scope #expected"(String scope, String expected) {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("micronaut-validation", [scope: scope])

        then:
        content.contains("data-lang=\"pyronaut\">[tool.pyronaut.dependencies]\n${expected} = [")

        where:
        scope                 | expected
        'compile'             | 'runtime'
        'implementation'      | 'runtime'
        'api'                 | 'runtime'
        'runtime'             | 'runtime'
        'runtimeOnly'         | 'runtime'
        'annotationProcessor' | 'build'
        'kapt'                | 'build'
        'compileOnly'         | 'build'
        'developmentOnly'     | 'build'
        'provided'            | 'build'
        'test'                | 'test'
        'testCompile'         | 'test'
        'testImplementation'  | 'test'
        'testRuntimeOnly'     | 'test'
        'testAnnotationProcessor' | 'test'
    }

    void "the pyronaut scope can be overridden with pyronautScope"() {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("micronaut-validation", ["text": 'scope="compileOnly", pyronautScope="runtime"'])

        then:
        content.contains('data-lang="pyronaut">[tool.pyronaut.dependencies]\nruntime = [')
    }

    void "the pyronaut scope is derived from a gradle only scope"() {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("micronaut-validation-processor", ["text": 'gradleScope="annotationProcessor"'])

        then:
        content.contains('data-lang="pyronaut">[tool.pyronaut.dependencies]\nbuild = [')
    }

    void "the pyronaut snippet is omitted for JVM only dependencies"() {
        when:
        String content = BuildDependencyMacro.contentForTargetAndAttributes("micronaut-kotlin-runtime", ["text": 'pyronaut="false"'])

        then:
        !content.contains('data-lang="pyronaut"')
        content.contains('data-lang="gradle">implementation')
        content.contains('data-lang="maven">')
    }
}
