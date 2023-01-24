package io.micronaut.docs

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import spock.lang.Specification

class ConfigurationPropertiesMacroSpec extends Specification {
    private Asciidoctor asciidoctor
    private String converted
    private Options options

    def setup() {
        asciidoctor = Asciidoctor.Factory.create()
        asciidoctor.javaExtensionRegistry().block(new ConfigurationPropertiesMacro(asciidoctor))
        options = Options.builder()
                .safe(SafeMode.SAFE)
                .attributes(
                        Attributes.builder()
                                .attribute('source-highlighter', 'highlightjs')
                                .build()
                )
                .backend("html5")
                .build()
    }

    def cleanup() {
        asciidoctor.shutdown()
    }

    def "converts properties"() {
        when:
        convert """

[configuration]
----
micronaut:
    server:
        port: 8080

mongodb:
    uri: mongodb://username:password@localhost:27017/databaseName
----

"""
        then:
        converted == '''<div class="openblock">
<div class="content">
<div class="listingblock multi-language-sample">
<div class="content">
<pre class="highlightjs highlight"><code class="language-properties hljs" data-lang="properties">micronaut.server.port=8080
mongodb.uri=mongodb://username:password@localhost:27017/databaseName</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="content">
<pre class="highlightjs highlight"><code class="language-yaml hljs" data-lang="yaml">micronaut:
    server:
        port: 8080

mongodb:
    uri: mongodb://username:password@localhost:27017/databaseName</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="content">
<pre class="highlightjs highlight"><code class="language-toml hljs" data-lang="toml">[micronaut]
  [micronaut.server]
    port=8080
[mongodb]
  uri="mongodb://username:password@localhost:27017/databaseName"</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="content">
<pre class="highlightjs highlight"><code class="language-groovy-config hljs" data-lang="groovy-config">micronaut {
  server {
    port = 8080
  }
}
mongodb {
  uri = "mongodb://username:password@localhost:27017/databaseName"
}</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="content">
<pre class="highlightjs highlight"><code class="language-hocon hljs" data-lang="hocon">{
  micronaut {
    server {
      port = 8080
    }
  }
  mongodb {
    uri = "mongodb://username:password@localhost:27017/databaseName"
  }
}</code></pre>
</div>
</div>
<div class="listingblock multi-language-sample">
<div class="content">
<pre class="highlightjs highlight"><code class="language-json-config hljs" data-lang="json-config">{
  "micronaut": {
    "server": {
      "port": 8080
    }
  },
  "mongodb": {
    "uri": "mongodb://username:password@localhost:27017/databaseName"
  }
}</code></pre>
</div>
</div>
</div>
</div>'''
    }

    void convert(String input) {
        converted = asciidoctor.convert(input, options)
    }
}
