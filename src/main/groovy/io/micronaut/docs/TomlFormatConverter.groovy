package io.micronaut.docs

import groovy.transform.CompileStatic
import org.tomlj.HoconSerializer
import org.tomlj.JavaPropertiesSerializer
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.YamlSerializer

@CompileStatic
class TomlFormatConverter {
    private static final String INDENT = "  "

    private final TomlParseResult tomlParseResult

    TomlFormatConverter(String content) {
        this.tomlParseResult = Toml.parse(content)
    }

    String toHocon() {
        def sb = new StringBuilder()
        HoconSerializer.toHocon(tomlParseResult, sb)
        sb.toString()
    }

    String toYaml() {
        def sb = new StringBuilder()
        YamlSerializer.toYaml(tomlParseResult, sb)
        sb.toString()
    }

    String toJavaProperties() {
        StringBuilder sb = new StringBuilder()
        JavaPropertiesSerializer.toJavaProperties(tomlParseResult, sb)
        return sb.toString()
    }

}
