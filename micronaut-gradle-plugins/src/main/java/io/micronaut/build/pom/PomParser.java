package io.micronaut.build.pom;

import org.gradle.api.GradleException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PomParser {
    private final PomDownloader pomDownloader;

    PomParser(PomDownloader pomDownloader) {
        this.pomDownloader = pomDownloader;
    }

    PomFile parse(File pomFile, String groupId, String artifactId, String version) {
        Element pom = readPom(pomFile);
        boolean bom = "pom".equals(childText(pom, "packaging"));
        Map<String, String> properties = readProperties(pom);
        properties.putIfAbsent("project.version", version);
        properties = resolve(properties);

        Element parent = child(pom, "parent").orElse(null);
        if (parent != null) {
            String parentGroupId = childText(parent, "groupId");
            String parentArtifactId = childText(parent, "artifactId");
            String parentVersion = childText(parent, "version");
            if (!parentGroupId.isEmpty() && !parentArtifactId.isEmpty() && !parentVersion.isEmpty()) {
                Optional<File> parentPom = pomDownloader.tryDownloadPom(new PomDependency(
                    false,
                    parentGroupId,
                    parentArtifactId,
                    parentVersion,
                    ""
                ));
                if (parentPom.isPresent()) {
                    PomFile parentFile = parse(parentPom.get(), parentGroupId, parentArtifactId, parentVersion);
                    parentFile.getProperties().forEach(properties::putIfAbsent);
                }
            }
        }

        Map<String, String> resolvedProperties = properties;
        List<PomDependency> dependencies = new ArrayList<>();
        child(pom, "dependencies")
            .map(dependenciesElement -> children(dependenciesElement, "dependency"))
            .orElseGet(List::of)
            .forEach(dependency -> dependencies.add(parseDependency(dependency, groupId, false, resolvedProperties)));
        child(pom, "dependencyManagement")
            .flatMap(dependencyManagement -> child(dependencyManagement, "dependencies"))
            .map(dependenciesElement -> children(dependenciesElement, "dependency"))
            .orElseGet(List::of)
            .forEach(dependency -> dependencies.add(parseDependency(dependency, groupId, true, resolvedProperties)));

        return new PomFile(groupId, artifactId, version, bom, dependencies, properties);
    }

    static PomDependency parseDependency(Element model, String group, boolean managed, Map<String, String> properties) {
        String depGroup = childText(model, "groupId").replace("${project.groupId}", group);
        String depArtifact = childText(model, "artifactId");
        String depVersion = substitute(properties, childText(model, "version"));
        String depScope = childText(model, "scope");
        return new PomDependency(managed, depGroup, depArtifact, depVersion, depScope);
    }

    private static Element readPom(File pomFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(pomFile);
            document.getDocumentElement().normalize();
            return document.getDocumentElement();
        } catch (ParserConfigurationException e) {
            throw new GradleException("Unable to configure POM parser", e);
        } catch (Exception e) {
            throw new GradleException("Unable to parse POM file " + pomFile, e);
        }
    }

    private static Map<String, String> readProperties(Element pom) {
        Map<String, String> result = new LinkedHashMap<>();
        child(pom, "properties").ifPresent(properties -> childElements(properties).forEach(property -> {
            result.put(nameOf(property), property.getTextContent());
        }));
        return result;
    }

    private static Optional<Element> child(Element parent, String name) {
        return childElements(parent).stream()
            .filter(element -> name.equals(nameOf(element)))
            .findFirst();
    }

    private static List<Element> children(Element parent, String name) {
        return childElements(parent).stream()
            .filter(element -> name.equals(nameOf(element)))
            .toList();
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static String childText(Element parent, String name) {
        return child(parent, name)
            .map(Element::getTextContent)
            .orElse("");
    }

    private static String nameOf(Element element) {
        String localName = element.getLocalName();
        return localName == null ? element.getNodeName() : localName;
    }

    private static String substitute(Map<String, String> properties, String value) {
        boolean substituteProperties = true;
        while (substituteProperties) {
            substituteProperties = false;
            for (Map.Entry<String, String> property : properties.entrySet()) {
                String token = "${" + property.getKey() + "}";
                if (value.contains(token)) {
                    value = value.replace(token, property.getValue());
                    substituteProperties = true;
                    break;
                }
            }
        }
        return value;
    }

    private static Map<String, String> resolve(Map<String, String> properties) {
        Map<String, String> result = new LinkedHashMap<>();
        properties.forEach((key, value) -> result.put(key, substitute(properties, value)));
        return result;
    }
}
