package io.micronaut.docs.internal;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing a Grails user guide table of contents defined in YAML.
 */
public class YamlTocStrategy {
    private final Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()));
    private final FileResourceChecker resourceChecker;
    private final String ext;

    public YamlTocStrategy(FileResourceChecker resourceChecker) {
        this(resourceChecker, ".gdoc");
    }

    public YamlTocStrategy(FileResourceChecker resourceChecker, String ext) {
        this.resourceChecker = resourceChecker;
        this.ext = ext;
    }

    public UserGuideNode generateToc(File yaml) {
        return load(yaml);
    }

    public UserGuideNode generateToc(String yaml) {
        return load(yaml);
    }

    protected UserGuideNode load(String yaml) {
        return process(parser.load(yaml));
    }

    protected UserGuideNode load(File file) {
        try (InputStream input = Files.newInputStream(file.toPath())) {
            return process(parser.load(input));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected UserGuideNode load(InputStream input) {
        return process(parser.load(input));
    }

    protected UserGuideNode load(Reader input) {
        return process(parser.load(input));
    }

    private UserGuideNode process(Object yamlDoc) {
        UserGuideNode rootNode = new UserGuideNode();
        processSection(yamlDoc, rootNode);
        return rootNode;
    }

    private void processSection(Object sections, UserGuideNode node) {
        if (sections instanceof Map<?, ?> sectionMap) {
            processSectionMap(sectionMap, node);
        } else if (sections instanceof String title) {
            node.setTitle(title);
        }
    }

    private void processSectionMap(Map<?, ?> sections, UserGuideNode node) {
        Map<Object, Object> remainingSections = new LinkedHashMap<>(sections);
        Object title = remainingSections.remove("title");
        if (title != null) {
            node.setTitle(title.toString());
        }

        for (Map.Entry<?, ?> section : remainingSections.entrySet()) {
            String name = section.getKey().toString();
            UserGuideNode child = new UserGuideNode();
            child.setParent(node);
            child.setName(name);
            child.setFile(determineFilePath(name, node));
            node.getChildren().add(child);
            processSection(section.getValue(), child);
        }
    }

    private String determineFilePath(String basename, UserGuideNode parent) {
        List<String> pathElements = new ArrayList<>();
        UserGuideNode node = parent;
        while (node.getName() != null) {
            pathElements.add(node.getName());
            node = node.getParent();
        }

        String filePath = basename + ext;
        if (resourceChecker.exists(filePath)) {
            return filePath;
        }
        if (!pathElements.isEmpty()) {
            for (int i = 1; i <= pathElements.size(); i++) {
                List<String> elements = new ArrayList<>();
                for (int j = pathElements.size() - 1; j >= pathElements.size() - i; j--) {
                    elements.add(pathElements.get(j));
                }
                filePath = String.join("/", elements) + "/" + basename + ext;
                if (resourceChecker.exists(filePath)) {
                    return filePath;
                }
            }
        }

        return null;
    }
}
