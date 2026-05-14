/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.micronaut.docs.asciidoc.AsciiDocEngine;
import io.micronaut.docs.internal.FileResourceChecker;
import io.micronaut.docs.internal.LegacyTocStrategy;
import io.micronaut.docs.internal.StringEscapeCategory;
import io.micronaut.docs.internal.UserGuideNode;
import io.micronaut.docs.internal.YamlTocStrategy;
import io.micronaut.docs.macros.HiddenMacro;
import io.micronaut.docs.macros.LanguageSnippetMacro;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.engine.context.BaseRenderContext;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Coordinates the {@link DocEngine} to produce documentation based on the gdoc format.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public class DocPublisher implements GuidePublisher {
    public static final String TOC_FILENAME = "toc.yml";

    private File src;
    private File target;
    private File docResources;
    private File apiDir;
    private File images;
    private File css;
    private File fonts;
    private File js;
    private File style;
    private File propertiesFile;
    private DocFileOperations fileOperations = new DefaultDocFileOperations();
    private String language = "";
    private String encoding = "UTF-8";
    private String title;
    private String subtitle = "";
    private String version;
    private String authors = "";
    private String translators = "";
    private String license = "";
    private String copyright = "";
    private String footer = "";
    private String logo;
    private String sponsorLogo;
    private String sourceRepo;
    private Properties engineProperties = new Properties();
    private boolean asciidoc;
    private BaseInitialRenderContext context;
    private DocEngine engine;
    private final List<Object> customMacros = new ArrayList<>();

    public DocPublisher() {
        this(null, null);
    }

    public DocPublisher(File src, File target) {
        this.src = src;
        this.target = target;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("grails/doc/doc.properties")) {
            if (input != null) {
                engineProperties.load(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Properties getEngineProperties() {
        return engineProperties;
    }

    @Override
    public void setEngineProperties(Properties properties) {
        engineProperties = properties;
    }

    public void registerMacro(Object macro) {
        customMacros.add(macro);
    }

    @Override
    public void registerHiddenMacro() {
        registerMacro(new HiddenMacro());
    }

    @Override
    public void publish() {
        initialize();
        if (src == null || !src.exists()) {
            return;
        }
        publishInitialized();
    }

    private void publishInitialized() {
        String refDocsDir = calculateLanguageDir(target == null ? "./docs" : target.getAbsolutePath(), "");
        File refDocsDirectory = new File(refDocsDir);
        File refGuideDir = new File(refDocsDirectory, "guide");
        File refPagesDir = new File(refGuideDir, "pages");

        fileOperations.mkdir(refDocsDirectory);
        fileOperations.mkdir(refGuideDir);
        fileOperations.mkdir(refPagesDir);

        File imgsDir = new File(refDocsDirectory, calculatePathToResources("img"));
        File fontsDir = new File(refDocsDirectory, calculatePathToResources("fonts"));
        File cssDir = new File(refDocsDirectory, calculatePathToResources("css"));
        File jsDir = new File(refDocsDirectory, calculatePathToResources("js"));
        fileOperations.mkdir(imgsDir);
        fileOperations.mkdir(cssDir);
        fileOperations.mkdir(jsDir);

        fileOperations.copy(
            imgsDir,
            DocFileOperations.CopySource.of(new File(docResources, "img")),
            DocFileOperations.CopySource.of(images)
        );
        fileOperations.copy(
            cssDir,
            DocFileOperations.CopySource.including(new File(docResources, "css"), "custom.css", "multi-language-sample.css"),
            DocFileOperations.CopySource.of(css)
        );
        fileOperations.copy(fontsDir, DocFileOperations.CopySource.of(fonts));
        fileOperations.copy(
            jsDir,
            DocFileOperations.CopySource.of(new File(docResources, "js")),
            DocFileOperations.CopySource.of(js)
        );

        File guideSrcDir = new File(src, "guide");
        String ext = asciidoc ? ".adoc" : ".gdoc";
        UserGuideNode guide = buildGuide(guideSrcDir, ext);
        Map<?, ?> legacyLinks = readLegacyLinks(guideSrcDir);

        Handlebars templateEngine = newTemplateEngine();
        List<ReferenceCategory> refCategories = referenceCategories(ext);
        String extraCss = extraCss();

        String pathToRoot = "..";
        Map<String, Object> vars = newVars();
        vars.put("encoding", encoding);
        vars.put("title", title);
        vars.put("docTitle", title);
        vars.put("subtitle", subtitle);
        vars.put("footer", footer);
        vars.put("authors", authors);
        vars.put("translators", translators);
        vars.put("version", version);
        vars.put("refMenu", refCategories);
        vars.put("toc", guide);
        vars.put("copyright", copyright);
        vars.put("logo", injectPath(logo, pathToRoot));
        vars.put("sponsorLogo", injectPath(sponsorLogo, pathToRoot));
        vars.put("single", false);
        vars.put("path", pathToRoot);
        vars.put("resourcesPath", calculatePathToResources(pathToRoot));
        vars.put("prev", null);
        vars.put("next", null);
        vars.put("legacyLinks", legacyLinks);
        vars.put("sourceRepo", sourceRepo);
        vars.put("extraCss", extraCss);

        configureAsciiDocEngine();

        Template guideTemplate = compileTemplate(templateEngine, new File(docResources, "style/guideItem.html"));
        Template sectionTemplate = compileTemplate(templateEngine, new File(docResources, "style/section.html"));
        StringBuilder fullContents = new StringBuilder();
        List<UserGuideNode> chapters = children(guide);
        for (int i = 0; i < chapters.size(); i++) {
            UserGuideNode chapter = chapters.get(i);
            Map<String, Object> chapterVars = new LinkedHashMap<>(vars);
            chapterVars.put("chapterNumber", i + 1);
            chapterVars.put("prev", i == 0 ? null : chapters.get(i - 1));
            chapterVars.put("next", i == chapters.size() - 1 ? null : chapters.get(i + 1));
            chapterVars.put("sectionNumber", Integer.toString(i + 1));
            writeChapter(chapter, guideTemplate, sectionTemplate, guideSrcDir, refGuideDir.getPath(), fullContents, chapterVars);
        }

        writeReferencePages(templateEngine, refDocsDirectory, pathToRoot, vars, ext);
        writeGuideIndexes(templateEngine, refDocsDirectory, refGuideDir, fullContents, vars);

        writeRedirect(refDocsDirectory);
        System.out.println("Built user manual at " + refDocsDir + "/index.html");
    }

    private UserGuideNode buildGuide(File guideSrcDir, String ext) {
        File yamlTocFile = null;
        if (notBlank(language)) {
            yamlTocFile = new File(guideSrcDir, "toc-" + language + ".yml");
        }
        if (yamlTocFile == null || !yamlTocFile.exists()) {
            yamlTocFile = new File(guideSrcDir, TOC_FILENAME);
        }

        if (yamlTocFile.exists()) {
            YamlTocStrategy tocStrategy = new YamlTocStrategy(new FileResourceChecker(guideSrcDir), ext);
            UserGuideNode guide = tocStrategy.generateToc(yamlTocFile);
            List<String> files = guideFiles(guideSrcDir, ext);
            if (!verifyToc(guideSrcDir, files, guide)) {
                throw new IllegalStateException("Encountered errors while building table of contents. Aborting.");
            }
            for (UserGuideNode chapter : children(guide)) {
                overrideAliasesFromToc(chapter);
            }
            return guide;
        }

        List<File> files = filesMatching(guideSrcDir, file -> file.isFile() && file.getName().endsWith(ext));
        return (UserGuideNode) new LegacyTocStrategy().generateToc(files);
    }

    private Map<?, ?> readLegacyLinks(File guideSrcDir) {
        File legacyLinksFile = new File(guideSrcDir, "links.yml");
        if (!legacyLinksFile.exists()) {
            return Map.of();
        }
        try (InputStream input = Files.newInputStream(legacyLinksFile.toPath())) {
            Object loaded = newYaml().load(input);
            return loaded instanceof Map<?, ?> map ? map : Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<ReferenceCategory> referenceCategories(String ext) {
        File refDir = new File(src, "ref");
        List<File> files = filesMatching(refDir, file -> file.isDirectory() && !file.getName().startsWith("."));
        List<ReferenceCategory> categories = new ArrayList<>();
        for (File file : files) {
            List<File> sections = filesMatching(file, child -> child.isFile() && child.getName().endsWith(ext));
            categories.add(new ReferenceCategory(file.getName(), new File(src, "ref/" + file.getName() + ext), sections));
        }
        return categories;
    }

    private String extraCss() {
        StringBuilder extraCss = new StringBuilder();
        for (String lang : LanguageSnippetMacro.LANGS) {
            if (notBlank(language)) {
                String display = Objects.equals(lang, language) ? "block" : "none";
                extraCss.append(".lang-").append(lang).append(" { display: ").append(display).append(" }; ");
            } else {
                extraCss.append(".lang-").append(lang).append(" { display: block }; ");
            }
        }
        return extraCss.toString();
    }

    private void configureAsciiDocEngine() {
        if (engine instanceof AsciiDocEngine asciiDocEngine) {
            asciiDocEngine.getAttributes().put("version", version);
            asciiDocEngine.getAttributes().put("apiDocs", "http://docs.grails.org/" + version + "/api/");
            asciiDocEngine.getAttributes().put("sourceRepo", sourceRepo);
            engineProperties.forEach((key, value) -> asciiDocEngine.getAttributes().put(key.toString(), value));
        }
    }

    private void writeReferencePages(
        Handlebars templateEngine,
        File refDocsDirectory,
        String pathToRoot,
        Map<String, Object> vars,
        String ext
    ) {
        List<File> files = filesMatching(new File(src, "ref"), file -> true);
        Template template = compileTemplate(templateEngine, new File(docResources, "style/referenceItem.html"));

        pathToRoot = "../..";
        vars.put("logo", injectPath(logo, pathToRoot));
        vars.put("sponsorLogo", injectPath(sponsorLogo, pathToRoot));
        vars.put("path", pathToRoot);
        vars.put("resourcesPath", calculatePathToResources(pathToRoot));

        for (File file : files) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                String section = file.getName();
                vars.put("section", section);

                File sectionDir = new File(refDocsDirectory, "ref/" + section);
                fileOperations.mkdir(sectionDir);
                List<File> textiles = filesMatching(file, child -> child.isFile() && child.getName().endsWith(ext));
                File usageFile = new File(src, "ref/" + section + ext);
                if (usageFile.exists()) {
                    renderReferenceFile(template, vars, usageFile, new File(sectionDir, "Usage.html"), pathToRoot, "ref/" + usageFile.getName());
                }
                for (File txt : textiles) {
                    String name = withoutExtension(txt.getName());
                    renderReferenceFile(template, vars, txt, new File(sectionDir, name + ".html"), pathToRoot, "ref/" + section + "/" + txt.getName());
                }
            }
        }
    }

    private void renderReferenceFile(
        Template template,
        Map<String, Object> vars,
        File sourceFile,
        File targetFile,
        String pathToRoot,
        String sourcePath
    ) {
        context.set(DocEngine.SOURCE_FILE, sourceFile.getName());
        context.set(DocEngine.CONTEXT_PATH, pathToRoot);
        context.set(DocEngine.API_CONTEXT_PATH, vars.get("resourcesPath"));
        warn("Rendering document file " + sourceFile.getName());
        vars.put("content", engine.render(readString(sourceFile), context));
        vars.put("sourcePath", sourcePath);
        refreshRenderedTemplateFragments(vars);
        writeTemplate(targetFile, template, vars);
    }

    private void writeGuideIndexes(
        Handlebars templateEngine,
        File refDocsDirectory,
        File refGuideDir,
        StringBuilder fullContents,
        Map<String, Object> vars
    ) {
        vars.remove("section");
        vars.put("content", fullContents.toString());
        vars.put("single", true);

        String pathToRoot = "..";
        vars.put("logo", injectPath(logo, pathToRoot));
        vars.put("sponsorLogo", injectPath(sponsorLogo, pathToRoot));
        vars.put("path", pathToRoot);
        vars.put("resourcesPath", calculatePathToResources(pathToRoot));

        Template template = compileTemplate(templateEngine, new File(docResources, "style/layout.html"));
        refreshRenderedTemplateFragments(vars);
        File singleFile = new File(refGuideDir, "single.html");
        writeTemplate(singleFile, template, vars);

        vars.put("content", "");
        vars.put("single", false);
        refreshRenderedTemplateFragments(vars);
        writeTemplate(new File(refGuideDir, "index.html"), template, vars);

        pathToRoot = ".";
        vars.put("logo", injectPath(logo, pathToRoot));
        vars.put("sponsorLogo", injectPath(sponsorLogo, pathToRoot));
        vars.put("path", pathToRoot);
        vars.put("resourcesPath", calculatePathToResources(pathToRoot));
        refreshRenderedTemplateFragments(vars);
        writeTemplate(new File(refDocsDirectory, "index.html"), template, vars);

        copyFile(singleFile, new File(refGuideDir, "index.html"));
        fileOperations.delete(singleFile);
    }

    private void writeRedirect(File refDocsDirectory) {
        writeString(
            new File(refDocsDirectory, "index.html"),
            renderer().renderRedirect("guide/index.html")
        );
    }

    public void writeChapter(
        UserGuideNode section,
        Template layoutTemplate,
        Template sectionTemplate,
        File guideSrcDir,
        String targetDir,
        StringBuilder fullContents,
        Map<String, Object> vars
    ) {
        fullContents.append(writePage(section, layoutTemplate, sectionTemplate, guideSrcDir, targetDir, "", "..", 0, vars));
    }

    public String writePage(
        UserGuideNode section,
        Template layoutTemplate,
        Template sectionTemplate,
        File guideSrcDir,
        String targetDir,
        String subDir,
        String path,
        int level,
        Map<String, Object> vars
    ) {
        File sourceFile = new File(guideSrcDir, section.getFile());
        context.set(DocEngine.SOURCE_FILE, sourceFile);
        context.set(DocEngine.CONTEXT_PATH, path);

        Map<String, Object> varsCopy = new LinkedHashMap<>(vars);
        varsCopy.putAll(newVars());
        varsCopy.put("name", section.getName());
        varsCopy.put("title", section.getTitle());
        varsCopy.put("path", path);
        varsCopy.put("level", level);
        varsCopy.put("hLevel", level == 0 ? 1 : 2);
        varsCopy.put("sectionToc", section.getChildren());
        varsCopy.put("sourcePath", section.getFile());
        Object chapterNumber = varsCopy.get("chapterNumber");
        if (chapterNumber instanceof Number number) {
            varsCopy.put("prevChapterNumber", number.intValue() - 1);
            varsCopy.put("nextChapterNumber", number.intValue() + 1);
        } else {
            varsCopy.put("prevChapterNumber", null);
            varsCopy.put("nextChapterNumber", null);
        }

        warn("Rendering document file " + sourceFile.getName());
        varsCopy.put("content", engine.render(readString(sourceFile), context));

        String sectionContent = applyTemplate(sectionTemplate, varsCopy);
        StringBuilder accumulatedContent = new StringBuilder(sectionContent);

        int childLevel = level + 1;
        String sectionNumber = stringValue(varsCopy.get("sectionNumber"));
        int subSectionNumber = 1;
        for (UserGuideNode child : children(section)) {
            varsCopy.put("sectionNumber", sectionNumber + "." + subSectionNumber);
            accumulatedContent.append(writePage(child, layoutTemplate, sectionTemplate, guideSrcDir, targetDir, "pages", path, childLevel, varsCopy));
            subSectionNumber++;
        }
        varsCopy.put("sectionNumber", sectionNumber);

        if (notBlank(subDir)) {
            if (subDir.endsWith("/")) {
                subDir = subDir.substring(0, subDir.length() - 1);
            }
            targetDir = targetDir + "/" + subDir;
            varsCopy.put("path", "../" + path);
            varsCopy.put("logo", injectPath(logo, stringValue(varsCopy.get("path"))));
            varsCopy.put("sponsorLogo", injectPath(sponsorLogo, stringValue(varsCopy.get("path"))));
        }

        varsCopy.put("content", accumulatedContent.toString());
        refreshRenderedTemplateFragments(varsCopy);
        writeTemplate(new File(targetDir, section.getName() + ".html"), layoutTemplate, varsCopy);
        return stringValue(varsCopy.get("content"));
    }

    protected void initialize() {
        if (notBlank(language) && src != null) {
            File langDir = new File(src, language);
            if (langDir.exists()) {
                src = langDir;
            }
        }
        if (apiDir == null) {
            apiDir = target;
        }
        Properties props = engineProperties == null ? new Properties() : engineProperties;
        engineProperties = props;

        if (propertiesFile != null && propertiesFile.exists()) {
            loadPropertiesFile(props);
        }

        applyStringProperties(props);

        context = new BaseInitialRenderContext();
        initContext(context, "..");
        engine = asciidoc ? new AsciiDocEngine(context) : new DocEngine(context);
        engine.setEngineProperties(props);
        context.setRenderEngine(engine);

        for (Object macro : customMacros) {
            setInitialContextIfSupported(macro, context);
            engine.addMacro(macro);
        }
    }

    private void loadPropertiesFile(Properties props) {
        if (propertiesFile.getName().endsWith(".properties")) {
            try (InputStream input = Files.newInputStream(propertiesFile.toPath())) {
                props.load(input);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else if (propertiesFile.getName().endsWith(".yml")) {
            try (InputStream input = Files.newInputStream(propertiesFile.toPath())) {
                Iterable<Object> ymls = newYaml().loadAll(input);
                for (Object yml : ymls) {
                    if (yml instanceof Map<?, ?> ymlMap) {
                        Object grails = ymlMap.get("grails");
                        if (grails instanceof Map<?, ?> grailsMap) {
                            Object config = grailsMap.get("doc");
                            if (config instanceof Map<?, ?> configMap) {
                                flattenKeys(props, configMap, List.of(), true);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void flattenKeys(Map<Object, Object> flatConfig, Map<?, ?> currentMap, List<String> path, boolean forceStrings) {
        for (Map.Entry<?, ?> entry : currentMap.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String stringKey = String.valueOf(entry.getKey());
            if (value instanceof Map<?, ?> nestedMap) {
                List<String> nestedPath = new ArrayList<>(path);
                nestedPath.add(stringKey);
                flattenKeys(flatConfig, nestedMap, List.copyOf(nestedPath), forceStrings);
            } else {
                String fullKey = path.isEmpty() ? stringKey : String.join(".", path) + "." + stringKey;
                if (value instanceof Collection<?> collection) {
                    flatConfig.put(fullKey, forceStrings ? join(collection) : value);
                    int index = 0;
                    for (Object item : collection) {
                        String collectionKey = fullKey + "[" + index + "]";
                        flatConfig.put(collectionKey, forceStrings ? String.valueOf(item) : item);
                        index++;
                    }
                } else {
                    flatConfig.put(fullKey, forceStrings ? String.valueOf(value) : value);
                }
            }
        }
    }

    protected boolean verifyToc(File baseDir, Collection<String> gdocFiles, UserGuideNode toc) {
        boolean hasErrors = false;
        Set<String> sectionsFound = new HashSet<>();
        Set<String> gdocsNotInToc = new LinkedHashSet<>(gdocFiles);

        for (UserGuideNode chapter : children(toc)) {
            hasErrors |= verifyTocInternal(baseDir, chapter, sectionsFound, gdocsNotInToc, List.of());
        }

        if (!gdocsNotInToc.isEmpty()) {
            for (String gdoc : gdocsNotInToc) {
                warn("No TOC entry found for '" + gdoc + "'");
            }
        }
        return !hasErrors;
    }

    private boolean verifyTocInternal(
        File baseDir,
        UserGuideNode section,
        Set<String> existing,
        Set<String> gdocFiles,
        List<String> pathElements
    ) {
        boolean hasErrors = false;
        String fullName = pathElements.isEmpty() ? section.getName() : String.join("/", pathElements) + "/" + section.getName();

        if (existing.contains(section.getName())) {
            hasErrors = true;
            error("Duplicate section name: " + fullName);
        }

        String sectionFile = section.getFile();
        if (sectionFile == null || !new File(baseDir, sectionFile).exists()) {
            hasErrors = true;
            error("No file found for '" + fullName + "'");
        } else {
            gdocFiles.remove(sectionFile);
        }

        existing.add(section.getName());
        List<String> childPathElements = new ArrayList<>(pathElements);
        childPathElements.add(section.getName());
        for (UserGuideNode child : children(section)) {
            hasErrors |= verifyTocInternal(baseDir, child, existing, gdocFiles, List.copyOf(childPathElements));
        }
        return hasErrors;
    }

    private String calculateLanguageDir(String startPath, String endPath) {
        List<String> elements = new ArrayList<>();
        if (notBlank(startPath)) {
            elements.add(startPath);
        }
        if (notBlank(language)) {
            elements.add(language);
        }
        if (notBlank(endPath)) {
            elements.add(endPath);
        }
        return String.join("/", elements);
    }

    private String injectPath(String source, String path) {
        if (source == null) {
            return null;
        }
        Handlebars handlebars = newTemplateEngine();
        String handlebarsSource = source.replace("${path}", "{{path}}");
        try {
            return handlebars.compileInline(handlebarsSource).apply(Map.of("path", calculatePathToResources(path)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String calculatePathToResources(String pathToRoot) {
        return notBlank(language) ? "../" + pathToRoot : pathToRoot;
    }

    private Handlebars newTemplateEngine() {
        return new Handlebars();
    }

    private Template compileTemplate(Handlebars handlebars, File source) {
        try {
            return handlebars.compileInline(readString(source));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String applyTemplate(Template template, Map<String, Object> vars) {
        try {
            return template.apply(vars);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void refreshRenderedTemplateFragments(Map<String, Object> vars) {
        UserGuideNode prev = userGuideNode(vars.get("prev"));
        UserGuideNode next = userGuideNode(vars.get("next"));
        vars.put("prevPath", prev == null ? null : StringEscapeCategory.encodeAsUrlPath(prev.getName()));
        vars.put("nextPath", next == null ? null : StringEscapeCategory.encodeAsUrlPath(next.getName()));
        UserGuideNode toc = userGuideNode(vars.get("toc"));
        vars.put("tocTreeHtml", toc == null ? "" : renderTocTree(toc, bool(vars.get("single")), stringValue(vars.get("path"))));
        vars.put("guideSummaryItemsHtml", toc == null ? "" : renderGuideSummaryItems(toc, stringValue(vars.get("path"))));
        vars.put("sectionTocHtml", renderSectionToc(nodeList(vars.get("sectionToc")), Objects.toString(vars.get("chapterNumber"), null)));
        vars.put("refMenuHtml", renderReferenceMenu(refMenu(vars.get("refMenu")), stringValue(vars.get("path")), vars.get("section")));
    }

    private String renderTocTree(UserGuideNode toc, boolean single, String path) {
        List<UserGuideNode> children = children(toc);
        List<Renderer.TocNode> nodes = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            UserGuideNode topSection = children.get(i);
            nodes.add(tocNode(0, topSection, topSection, Integer.toString(i + 1), single, path));
        }
        return renderer().renderTocTree(new Renderer.TocTree(nodes));
    }

    private Renderer.TocNode tocNode(
        int level,
        UserGuideNode section,
        UserGuideNode topSection,
        String prefix,
        boolean single,
        String path
    ) {
        String sectionId = html(StringEscapeCategory.encodeAsUrlFragment(section.getName()));
        String sectionHref = single
            ? "#" + sectionId
            : path + "/guide/" + html(StringEscapeCategory.encodeAsUrlPath(topSection.getName())) + (level == 0 ? ".html" : ".html#" + sectionId);
        List<UserGuideNode> children = children(section);
        List<Renderer.TocNode> nodes = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            nodes.add(tocNode(level + 1, children.get(i), topSection, prefix + "." + (i + 1), single, path));
        }
        return new Renderer.TocNode(
            sectionId,
            sectionHref,
            sectionId,
            prefix,
            html(Objects.toString(section.getTitle(), "")),
            nodes
        );
    }

    private String renderGuideSummaryItems(UserGuideNode toc, String path) {
        List<UserGuideNode> children = children(toc);
        List<Renderer.GuideSummaryItem> items = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            UserGuideNode chapter = children.get(i);
            items.add(new Renderer.GuideSummaryItem(
                path,
                html(StringEscapeCategory.encodeAsUrlPath(chapter.getName())),
                Integer.toString(i + 1),
                html(Objects.toString(chapter.getTitle(), ""))
            ));
        }
        return renderer().renderGuideSummary(new Renderer.GuideSummary(items));
    }

    private String renderSectionToc(List<UserGuideNode> sections, String chapterNumber) {
        if (sections.isEmpty()) {
            return "";
        }
        List<Renderer.SectionTocItem> items = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            collectSectionTocItems(items, 0, sections.get(i), chapterNumber + "." + (i + 1));
        }
        return renderer().renderSectionToc(new Renderer.SectionToc(items));
    }

    private void collectSectionTocItems(List<Renderer.SectionTocItem> items, int level, UserGuideNode section, String prefix) {
        String sectionId = html(StringEscapeCategory.encodeAsUrlFragment(section.getName()));
        items.add(new Renderer.SectionTocItem(level * 10, sectionId, prefix, html(Objects.toString(section.getTitle(), ""))));
        List<UserGuideNode> children = children(section);
        for (int i = 0; i < children.size(); i++) {
            collectSectionTocItems(items, level + 1, children.get(i), prefix + "." + (i + 1));
        }
    }

    private String renderReferenceMenu(List<ReferenceCategory> refMenu, String path, Object selectedSection) {
        List<Renderer.ReferenceMenuCategory> categories = new ArrayList<>(refMenu.size());
        for (ReferenceCategory category : refMenu) {
            String catName = html(Objects.toString(category.name(), ""));
            String catPath = html(StringEscapeCategory.encodeAsUrlPath(Objects.toString(category.name(), "")));
            List<Renderer.ReferenceMenuItem> items = new ArrayList<>(category.sections().size());
            for (File txt : category.sections()) {
                String sectionName = withoutExtension(txt.getName());
                String sectionPath = html(StringEscapeCategory.encodeAsUrlPath(sectionName));
                items.add(new Renderer.ReferenceMenuItem(sectionPath, html(sectionName)));
            }
            categories.add(new Renderer.ReferenceMenuCategory(
                catName,
                Objects.equals(category.name(), selectedSection),
                path,
                catPath,
                category.usage().exists(),
                items
            ));
        }
        return renderer().renderReferenceMenu(new Renderer.ReferenceMenu(categories));
    }

    private Renderer renderer() {
        return engine == null ? DefaultRenderer.INSTANCE : engine.getRenderer();
    }

    private BaseRenderContext initContext(BaseRenderContext renderContext, String path) {
        renderContext.set(DocEngine.CONTEXT_PATH, path);
        renderContext.set(DocEngine.BASE_DIR, src.getAbsolutePath());
        renderContext.set(DocEngine.API_BASE_PATH, apiDir.getAbsolutePath());
        renderContext.set(DocEngine.API_CONTEXT_PATH, calculatePathToResources(path));
        renderContext.set(DocEngine.RESOURCES_CONTEXT_PATH, calculatePathToResources(path));
        return renderContext;
    }

    private void overrideAliasesFromToc(UserGuideNode node) {
        String file = node.getFile();
        if (file != null && file.endsWith(".gdoc")) {
            file = file.substring(0, file.length() - ".gdoc".length());
        }
        engineProperties.setProperty("alias." + node.getName(), file);
        for (UserGuideNode section : children(node)) {
            overrideAliasesFromToc(section);
        }
    }

    @Override
    public void setFileOperations(DocFileOperations fileOperations) {
        this.fileOperations = fileOperations == null ? new DefaultDocFileOperations() : fileOperations;
    }

    @Override
    public void setAsciidoc(boolean asciidoc) {
        this.asciidoc = asciidoc;
    }

    @Override
    public void setDocResources(File docResources) {
        this.docResources = docResources;
    }

    @Override
    public void setApiDir(File apiDir) {
        this.apiDir = apiDir;
    }

    @Override
    public void setLanguage(String language) {
        this.language = language == null ? "" : language;
    }

    @Override
    public void setSourceRepo(String sourceRepo) {
        this.sourceRepo = sourceRepo;
    }

    @Override
    public void setImages(File images) {
        this.images = images;
    }

    @Override
    public void setCss(File css) {
        this.css = css;
    }

    @Override
    public void setFonts(File fonts) {
        this.fonts = fonts;
    }

    @Override
    public void setJs(File js) {
        this.js = js;
    }

    @Override
    public void setStyle(File style) {
        this.style = style;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    public void setPropertiesFile(File propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    private void applyStringProperties(Properties props) {
        title = property(props, "title", title);
        subtitle = property(props, "subtitle", subtitle);
        version = property(props, "version", version);
        authors = property(props, "authors", authors);
        translators = property(props, "translators", translators);
        license = property(props, "license", license);
        copyright = property(props, "copyright", copyright);
        footer = property(props, "footer", footer);
        logo = property(props, "logo", logo);
        sponsorLogo = property(props, "sponsorLogo", sponsorLogo);
        sourceRepo = property(props, "sourceRepo", sourceRepo);
        encoding = property(props, "encoding", encoding);
        language = property(props, "language", language);
    }

    private Map<String, Object> newVars() {
        Map<String, Object> vars = new LinkedHashMap<>();
        engineProperties.forEach((key, value) -> vars.put(String.valueOf(key), value));
        return vars;
    }

    private List<String> guideFiles(File guideSrcDir, String ext) {
        if (!guideSrcDir.exists()) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(guideSrcDir.toPath())) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(ext))
                .map(path -> guideSrcDir.toPath().relativize(path).toString().replace(File.separatorChar, '/'))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<File> filesMatching(File directory, FileFilter filter) {
        if (directory == null) {
            return new ArrayList<>();
        }
        File[] files = directory.listFiles(filter);
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> result = new ArrayList<>(List.of(files));
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private String readString(File file) {
        try {
            return Files.readString(file.toPath(), Charset.forName(encoding));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeTemplate(File file, Template template, Map<String, Object> vars) {
        fileOperations.mkdir(file.getParentFile());
        try (Writer writer = Files.newBufferedWriter(file.toPath(), Charset.forName(encoding))) {
            template.apply(vars, writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeString(File file, String value) {
        fileOperations.mkdir(file.getParentFile());
        try {
            Files.writeString(file.toPath(), value, Charset.forName(encoding));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void copyFile(File source, File target) {
        fileOperations.mkdir(target.getParentFile());
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void setInitialContextIfSupported(Object macro, BaseRenderContext context) {
        for (Method method : macro.getClass().getMethods()) {
            if (method.getName().equals("setInitialContext") && method.getParameterCount() == 1) {
                try {
                    method.invoke(macro, context);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Cannot set initialContext on " + macro.getClass().getName(), e);
                }
                return;
            }
        }
    }

    private static List<UserGuideNode> children(UserGuideNode node) {
        if (node == null || node.getChildren() == null) {
            return List.of();
        }
        List<?> children = node.getChildren();
        List<UserGuideNode> result = new ArrayList<>(children.size());
        for (Object child : children) {
            result.add((UserGuideNode) child);
        }
        return result;
    }

    private static UserGuideNode userGuideNode(Object value) {
        return value instanceof UserGuideNode node ? node : null;
    }

    private static List<UserGuideNode> nodeList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<UserGuideNode> nodes = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof UserGuideNode node) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private static List<ReferenceCategory> refMenu(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<ReferenceCategory> categories = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof ReferenceCategory category) {
                categories.add(category);
            }
        }
        return categories;
    }

    private static String withoutExtension(String fileName) {
        return fileName.length() <= 5 ? fileName : fileName.substring(0, fileName.length() - 5);
    }

    private static String html(String value) {
        return StringEscapeCategory.encodeAsHtml(value == null ? "" : value);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String property(Properties props, String key, String currentValue) {
        Object value = props.get(key);
        return value == null ? currentValue : String.valueOf(value);
    }

    private static String join(Collection<?> collection) {
        List<String> values = new ArrayList<>(collection.size());
        for (Object item : collection) {
            values.add(String.valueOf(item));
        }
        return String.join(",", values);
    }

    private static Yaml newYaml() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    private static void warn(String message) {
        System.out.println(message);
    }

    private static void error(String message) {
        System.err.println(message);
    }

    private record ReferenceCategory(String name, File usage, List<File> sections) {
    }
}
