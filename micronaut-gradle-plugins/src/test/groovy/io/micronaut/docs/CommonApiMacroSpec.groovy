package io.micronaut.docs

class CommonApiMacroSpec extends AbstractConverterSpec {
    def "api module always adds io.micronaut prefix"() {
        when:
        convert "api:net.example.ServiceSettings[]"

        then:
        converted == '<div class="paragraph">\n<p><a href="../api/io/micronaut/net/example/ServiceSettings.html">ServiceSettings</a></p>\n</div>'
    }

    def "can configure custom package prefix for api"() {
        when:
        convert "api:net.example.ServiceSettings[packagePrefix='titi.tata.']"

        then:
        converted == '<div class="paragraph">\n<p><a href="../api/titi/tata/net/example/ServiceSettings.html">ServiceSettings</a></p>\n</div>'
    }

    def "can configure custom base uri for api"() {
        when:
        convert "api:net.example.ServiceSettings[defaultUri='/dadidou']"

        then:
        converted == '<div class="paragraph">\n<p><a href="/dadidou/io/micronaut/net/example/ServiceSettings.html">ServiceSettings</a></p>\n</div>'
    }

    def "can configure both custom base uri and package prefix for api"() {
        when:
        convert "api:net.example.ServiceSettings[defaultUri='/dadidou', packagePrefix='']"

        then:
        converted == '<div class="paragraph">\n<p><a href="/dadidou/net/example/ServiceSettings.html">ServiceSettings</a></p>\n</div>'
    }
}
