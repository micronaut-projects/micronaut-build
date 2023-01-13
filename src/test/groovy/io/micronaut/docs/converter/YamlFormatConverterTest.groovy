package io.micronaut.docs.converter

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import spock.lang.Specification

class YamlFormatConverterTest extends Specification {
    String toml
    String hocon
    String properties
    Map<String, Object> map

    def "converts a simple toml file"() {
        when:
        yaml = """
foo:
  bar: test
  baz: stuff
        """

        then:
        hasToml """
[foo]
  bar="test"
  baz="stuff"
  """

        hasProperties """
foo.bar=test
foo.baz=stuff
"""

        hasHocon """
{
  foo {
    bar = "test"
    baz = "stuff"
  }
}
"""
    }

    def "converts different types"() {
        when:
        yaml = """
some-int: 42
some-double: 2.5
some-boolean: false
some-simple-string: Hello
some-string-as-number: '42'
"""
        then:
        hasToml """
some-int=42
some-double=2.5
some-boolean=false
some-simple-string="Hello"
some-string-as-number="42"
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
        yaml = """
foo:
  - test
  - stuff
        """

        then:
        hasToml """
foo=[
  "test",
  "stuff"
]
"""

        hasProperties """
foo[0]=test
foo[1]=stuff

"""

        hasHocon """
{
  foo = ["test", "stuff"]
}
"""
    }

    def "converts nested structure"() {
        when:
        yaml = """
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

        then:
        hasToml """
[micronaut]
  [micronaut.application]
    name="demo"
  [micronaut.server]
    port=9090
[netty]
  [netty.default]
    [netty.default.allocator]
      max-order=3
"""
        hasHocon """
{
  micronaut {
    application {
      name = "demo"
    }
    server {
      port = 9090
    }
  }
  netty {
    default {
      allocator {
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
        yaml = """

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
    services:
      - id: proxy
        port: 8080
      - id: firewall
        port: 12444  
"""
        then:
        hasToml """
[[servers]]
  name="front"
  ip="10.0.0.1"
  tags=[
    "js",
    "react"
  ]
[[servers]]
  name="back"
  ip="10.0.0.2"
  tags=[
    "micronaut"
  ]
  [[servers.services]]
    id="proxy"
    port=8080
  [[servers.services]]
    id="firewall"
    port=12444
"""

        hasHocon """
{
  servers = [{
      name = "front"
      ip = "10.0.0.1"
      tags = ["js", "react"]
    }, {
      name = "back"
      ip = "10.0.0.2"
      tags = ["micronaut"]
      services = [{
          id = "proxy"
          port = 8080
        }, {
          id = "firewall"
          port = 12444
        }]
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
servers[1].services[0].id=proxy
servers[1].services[0].port=8080
servers[1].services[1].id=firewall
servers[1].services[1].port=12444
"""
    }

    def "converts entries with dots"() {
        when:

        yaml = """
logger:
  levels:
    ROOT: INFO
    io.github.jhipster.sample: INFO
    io.github.jhipster: INFO
"""
        then:

        hasToml """
[logger]
  [logger.levels]
    ROOT="INFO"
    "io.github.jhipster.sample"="INFO"
    "io.github.jhipster"="INFO"
"""

        hasHocon """{
  logger {
    levels {
      ROOT = "INFO"
      "io.github.jhipster.sample" = "INFO"
      "io.github.jhipster" = "INFO"
    }
  }
}"""

        hasProperties """
logger.levels.ROOT=INFO
logger.levels.io\\\\.github\\\\.jhipster\\\\.sample=INFO
logger.levels.io\\\\.github\\\\.jhipster=INFO
"""
    }

    private void hasToml(String toml) {
        assertEqualsTrimmed(this.toml, toml)
    }

    private void hasHocon(String hocon) {
        assertEqualsTrimmed(this.hocon, hocon)
        // make sure the generated HOCON can be parsed
        def config = ConfigFactory.parseString(hocon)
        def hoconMap = unwrap(config)
        //assert map == hoconMap
    }

    private static Map<String, Object> unwrap(Config config) {
        config.entrySet().collectEntries {
            [it.key, unwrap(it.value)]
        }
    }

    private static Object unwrap(ConfigValue value) {
        def unwrapped = value.unwrapped()
        unwrapped
    }

    private void hasProperties(String properties) {
        assertEqualsTrimmed(this.properties, properties)
        def props = new Properties()
        props.load(new StringReader(properties))
        println(props)
    }

    private static void assertEqualsTrimmed(String actual, String expected) {
        actual = actual.trim()
        expected = expected.trim()
        assert actual == expected
    }

    private void setYaml(String yaml) {
        YamlFormatConverter converter = new YamlFormatConverter(yaml)
        toml = converter.toToml()
        properties = converter.toJavaProperties()
        hocon = converter.toHocon()
        map = converter.asMap()
    }
}
