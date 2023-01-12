package io.micronaut.docs

import spock.lang.Specification

class TomlFormatConverterTest extends Specification {
    String yaml
    String properties
    String hocon

    def "converts a simple toml file"() {
        when:
        toml = """
foo.bar="test"
foo.baz="stuff"
        """

        then:
        hasYaml """foo: 
  bar: test
  baz: stuff
  """

        hasProperties """
foo.bar=test
foo.baz=stuff
"""

        hasHocon """
{
  foo: {
    bar = "test"
    baz = "stuff"
  }
}
"""
    }

    def "converts different types"() {
        when:
        toml = """
some-int = 42
some-double = 2.5
some-boolean = false
some-simple-string = "Hello"
some-string-as-number = "42"
"""
        then:
        hasYaml """
some-int: 42
some-double: 2.5
some-boolean: false
some-simple-string: Hello
some-string-as-number: '42'
"""

        hasHocon """
{
  some-int = 42
  some-double = 2.5
  some-boolean = false
  some-simple-string = "Hello"
  some-string-as-number = "42"
}

"""

        hasProperties """
some-int=42
some-double=2.5
some-boolean=false
some-simple-string=Hello
some-string-as-number=42

"""
    }

    def "converts a simple array"() {
        when:
        toml = """
foo=["test", "stuff"]
        """

        then:
        hasYaml """
foo: 
  - test
  - stuff
"""

        hasProperties """
foo[0]=test
foo[1]=stuff

"""

        hasHocon """
{
  foo: [
    "test",
    "stuff"
  ]
}
"""
    }

    def "converts nested structure"() {
        when:
        toml = """
[micronaut]
application.name = "demo"
server.port = 9090

[netty]
default.allocator.max-order = 3

"""

        then:
        hasYaml """
micronaut: 
  application: 
    name: demo
  server: 
    port: 9090
netty: 
  default: 
    allocator: 
      max-order: 3
"""
        hasHocon """
{
  micronaut: {
    application: {
      name = "demo"
    }
    server: {
      port = 9090
    }
  }
  netty: {
    default: {
      allocator: {
        max-order = 3
      }
    }
  }
}
"""
        hasProperties """
micronaut.application.name=demo
micronaut.server.port=9090
netty.default.allocator.max-order=3
"""
    }

    def "converts array of objects"() {
        when:
        toml = """
servers = [
   { name = "front", ip = "10.0.0.1", tags = ["js", "react"] },
   { name = "back", ip = "10.0.0.2", tags = ["micronaut"] }
]
"""
        then:
        hasYaml """
servers: 
  - name: front
    ip: 10.0.0.1
    tags: 
      - js
      - react

  - name: back
    ip: 10.0.0.2
    tags: 
      - micronaut
"""

        hasHocon """
{
  servers: [{
    name = "front"
    ip = "10.0.0.1"
    tags: [
      "js",
      "react"
    ]
  },{
    name = "back"
    ip = "10.0.0.2"
    tags: [
      "micronaut"
    ]
  }]
}
"""

        hasProperties """
servers[0].name=front
servers[0].ip=10.0.0.1
servers[0].tags[0]=js
servers[0].tags[1]=react
servers[1].name=back
servers[1].ip=10.0.0.2
servers[1].tags[0]=micronaut
"""
    }

    private void hasYaml(String yaml) {
        assertEqualsTrimmed(this.yaml, yaml)
    }

    private void hasHocon(String hocon) {
        assertEqualsTrimmed(this.hocon, hocon)
    }

    private void hasProperties(String properties) {
        assertEqualsTrimmed(this.properties, properties)
    }

    private static void assertEqualsTrimmed(String actual, String expected) {
        actual = actual.trim()
        expected = expected.trim()
        assert actual == expected
    }

    private void setToml(String toml) {
        TomlFormatConverter converter = new TomlFormatConverter(toml)
        yaml = converter.toYaml()
        properties = converter.toJavaProperties()
        hocon = converter.toHocon()
    }
}
