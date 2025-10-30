package io.micronaut.docs.converter;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class YamlFormatConverter {
    private final Map<String, Object> model = new LinkedHashMap<>();

    public YamlFormatConverter(String content) {
        Yaml yaml = new Yaml();
        Iterable<Object> objects;
        try {
            objects = yaml.loadAll(content);
            for (Object object : objects) {
                if (object instanceof Map) {
                    model.putAll((Map<String, Object>) object);
                } else {
                    throw new YAMLException("Unexpected YAML object type: " + object.getClass());
                }
            }
        } catch (YAMLException e) {
            System.err.println("Invalid sample YAML discovered:\n");
            System.err.println("----");
            System.err.println(content);
            System.err.println("----");
            throw e;
        }
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(model);
    }

    public String toToml() {
        return convert(new TomlGenerator(model));
    }

    public String toJavaProperties() {
        return convert(new JavaPropertiesGenerator(model));
    }

    public String toHocon() {
        return convert(new HoconGenerator(model));
    }

    public String toJson() {
        return convert(new JsonGenerator(model));
    }

    public String toGroovy() {
        return convert(new ConfigSlurperGenerator(model));
    }

    private String convert(AbstractModelVisitor generator) {
        generator.visit();
        return generator.toString();
    }

}
