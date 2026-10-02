/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.desugar.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Desugars the lambdas of one module: reads the classes {@code compileJava} wrote, writes a full copy of them in
 * which every rewritable lambda call site calls a generated class, and reports what it did.
 *
 * <p>Each nest is rewritten as a unit. Every rewritten class is verified against the module's runtime class path
 * and accepted only when it verifies no worse than the class the compiler wrote; every generated class must
 * verify cleanly. When any class of a nest fails, the whole nest is copied as compiled and its sites are reported
 * under {@link LambdaDesugarer.Reason#NEST_FALLBACK}. The step never fails a build over a class it cannot
 * rewrite.</p>
 *
 * <p>Native image keeps the instances of a lambda in the image heap when build-time initialized code creates them,
 * such as the converters of the shared conversion service, because it initializes the classes the JDK spins at build
 * time. A generated class is an ordinary class, initialized at run time by default, and such an instance would fail
 * the image build. So the copy also holds a {@code native-image.properties} that initializes the generated classes
 * at build time: they have no static state but the singleton of a capture-free site.</p>
 */
final class ModuleDesugaring {

    private static final String CLASS_SUFFIX = ".class";

    private static final String META_INF = "META-INF/";

    /** Where, under the module's native image directory, the configuration of the generated classes goes. */
    static final String NATIVE_IMAGE_PROPERTIES = "desugared-lambdas/native-image.properties";

    private static final ClassFile REWRITER = ClassFile.of(ClassFile.ConstantPoolSharingOption.SHARED_POOL,
            ClassFile.DebugElementsOption.PASS_DEBUG,
            ClassFile.LineNumbersOption.PASS_LINE_NUMBERS,
            ClassFile.AttributesProcessingOption.PASS_ALL_ATTRIBUTES,
            ClassFile.StackMapsOption.DROP_STACK_MAPS);

    private ModuleDesugaring() {
    }

    /**
     * Desugars a module.
     *
     * @param classesDirectory the classes {@code compileJava} wrote
     * @param classpath        what the module runs with, in order: its other classes directories and its runtime
     *                         class path
     * @param compileClasspath the module's compile class path
     * @param output           the directory that receives the desugared copy of {@code classes}; emptied first
     * @param report           the report file
     * @param nativeImageName  the directory under {@code META-INF/native-image/} for the native image
     *                         configuration, such as {@code io.micronaut/micronaut-core}, or {@code null} for none
     * @return a one-line summary
     * @throws IOException if a class cannot be read or written
     */
    static String run(Path classesDirectory, List<Path> classpath, List<Path> compileClasspath, Path output,
                      Path report, String nativeImageName) throws IOException {
        Path classes = classesDirectory.toAbsolutePath().normalize();
        delete(output);
        Files.createDirectories(output);
        List<String> entries = list(classes);
        List<Path> roots = new ArrayList<>(classpath.size() + 1);
        roots.add(classes);
        for (Path path : classpath) {
            if (!path.toAbsolutePath().normalize().equals(classes)) {
                roots.add(path);
            }
        }
        Map<String, byte[]> written = new HashMap<>();
        Map<String, byte[]> generated = new LinkedHashMap<>();
        LambdaDesugarer.Plan plan;
        List<String> notes = new ArrayList<>();
        Set<String> fallenBack = new HashSet<>();
        int rewrittenSites = 0;
        int fallbackSites = 0;
        try (ClassPathModel model = ClassPathModel.open(roots);
             ClassPathModel compile = ClassPathModel.open(compileClasspath)) {
            Map<String, byte[]> hosts = new LinkedHashMap<>();
            for (String entry : entries) {
                if (isCandidate(entry)) {
                    byte[] bytes = Files.readAllBytes(classes.resolve(entry));
                    if (LambdaDesugarer.matches(bytes)) {
                        hosts.put(entry, bytes);
                    }
                }
            }
            LambdaDesugarer desugarer = new LambdaDesugarer(model, compile, entry -> {
                Path file = classes.resolve(entry);
                return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
            });
            plan = desugarer.plan(hosts);
            for (LambdaDesugarer.Unit unit : plan.units()) {
                List<String> names = unit.generatedNames();
                String failure = rewrite(unit, model, written, generated);
                if (failure == null) {
                    rewrittenSites += names.size();
                } else {
                    fallbackSites += names.size();
                    fallenBack.addAll(names);
                    notes.add(unit.nestHostEntry() + ": " + failure);
                }
            }
        }
        for (String entry : entries) {
            Path target = output.resolve(entry);
            Files.createDirectories(target.getParent());
            byte[] bytes = written.get(entry);
            if (bytes != null) {
                Files.write(target, bytes);
            } else {
                Files.copy(classes.resolve(entry), target);
            }
        }
        for (Map.Entry<String, byte[]> entry : generated.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
        if (nativeImageName != null && !generated.isEmpty()) {
            String entry = META_INF + "native-image/" + nativeImageName + "/" + NATIVE_IMAGE_PROPERTIES;
            if (!entries.contains(entry)) {
                writeNativeImageProperties(output.resolve(entry), generated.keySet());
            }
        }
        Map<LambdaDesugarer.Reason, Integer> kept = new EnumMap<>(LambdaDesugarer.Reason.class);
        for (LambdaDesugarer.Site site : plan.sites()) {
            if (site.reason() != null) {
                kept.merge(site.reason(), 1, Integer::sum);
            }
        }
        if (fallbackSites > 0) {
            kept.merge(LambdaDesugarer.Reason.NEST_FALLBACK, fallbackSites, Integer::sum);
        }
        int keptSites = kept.values().stream().mapToInt(Integer::intValue).sum();
        writeReport(report, plan, fallenBack, rewrittenSites, generated.size(), written.size(), kept, notes);
        return "Desugared " + rewrittenSites + " lambda call sites into " + generated.size()
                + " generated classes (" + written.size() + " classes rewritten, " + keptSites
                + " sites kept as compiled); report: " + report;
    }

    /**
     * Rewrites one nest and verifies it.
     *
     * @return {@code null} when the nest was accepted, otherwise why it was kept as compiled
     */
    private static String rewrite(LambdaDesugarer.Unit unit, ClassPathModel model, Map<String, byte[]> written,
                                  Map<String, byte[]> generated) {
        Map<ClassDesc, ClassDesc> own = new HashMap<>();
        for (String name : unit.generatedNames()) {
            own.put(ClassDesc.ofInternalName(name), ConstantDescs.CD_Object);
        }
        ClassFile verifier = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                ClassHierarchyResolver.of(List.of(), own).orElse(model)));
        Map<String, byte[]> rewritten = new LinkedHashMap<>();
        Map<String, byte[]> classes;
        try {
            for (Map.Entry<String, LambdaDesugarer.ClassPlan> entry : unit.classes().entrySet()) {
                byte[] original = entry.getValue().original();
                ClassModel parsed = REWRITER.parse(original);
                ClassTransform transform = entry.getValue().transform()
                        .andThen(OriginalFrames.of(parsed).reattaching());
                byte[] bytes = REWRITER.transformClass(parsed, transform);
                List<String> errors = messages(verifier.verify(bytes));
                if (!errors.isEmpty()) {
                    String grown = grown(errors, messages(verifier.verify(original)));
                    if (grown != null) {
                        return entry.getKey() + ": verification: " + grown;
                    }
                }
                rewritten.put(entry.getKey(), bytes);
            }
            classes = unit.generate();
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                List<String> errors = messages(verifier.verify(entry.getValue()));
                if (!errors.isEmpty()) {
                    return entry.getKey() + ": verification: " + errors.get(0);
                }
            }
        } catch (RuntimeException | LinkageError | AssertionError | StackOverflowError e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
        written.putAll(rewritten);
        generated.putAll(classes);
        return null;
    }

    private static void writeNativeImageProperties(Path file, Collection<String> generated) throws IOException {
        List<String> names = new ArrayList<>(generated.size());
        for (String entry : generated) {
            names.add(entry.substring(0, entry.length() - CLASS_SUFFIX.length()).replace('/', '.'));
        }
        names.sort(null);
        Files.createDirectories(file.getParent());
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("# The lambda classes micronaut-build generated (micronaut-build#956), initialized at build time as\n");
            out.write("# native image initializes the lambda classes the JDK spins.\n");
            out.write("Args = --initialize-at-build-time=");
            for (int i = 0; i < names.size(); i++) {
                out.write(names.get(i));
                out.write(i < names.size() - 1 ? ",\\\n    " : "\n");
            }
        }
    }

    private static List<String> messages(List<VerifyError> errors) {
        List<String> messages = new ArrayList<>(errors.size());
        for (VerifyError error : errors) {
            messages.add(String.valueOf(error.getMessage()));
        }
        return messages;
    }

    /**
     * The first verification error of a rewritten class that the class the compiler wrote does not have: a class
     * may already fail to verify against the runtime class path, typically because it references a compile-only
     * dependency. Two messages are the same error when they are equal without the bytecode offset they name.
     *
     * @return the first new error, or {@code null} when the rewrite verifies no worse
     */
    static String grown(List<String> rewritten, List<String> original) {
        Map<String, Integer> known = new HashMap<>();
        for (String error : original) {
            known.merge(withoutOffset(error), 1, Integer::sum);
        }
        for (String error : rewritten) {
            String key = withoutOffset(error);
            Integer left = known.get(key);
            if (left == null || left == 0) {
                return error;
            }
            known.put(key, left - 1);
        }
        return null;
    }

    private static String withoutOffset(String error) {
        return error.replaceAll("@\\d+", "@");
    }

    private static boolean isCandidate(String entry) {
        return entry.endsWith(CLASS_SUFFIX) && !entry.startsWith(META_INF) && !ClassPathModel.isModuleInfo(entry);
    }

    private static List<String> list(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static void delete(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void writeReport(Path report, LambdaDesugarer.Plan plan, Set<String> fallenBack, int rewrittenSites,
                                    int generatedClasses, int rewrittenClasses,
                                    Map<LambdaDesugarer.Reason, Integer> kept, List<String> notes) throws IOException {
        Files.createDirectories(report.getParent());
        try (Writer out = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
            out.write("# Lambda desugaring report\n");
            out.write("rewrittenSites=" + rewrittenSites + "\n");
            out.write("generatedClasses=" + generatedClasses + "\n");
            out.write("rewrittenClasses=" + rewrittenClasses + "\n");
            out.write("keptSites=" + kept.values().stream().mapToInt(Integer::intValue).sum() + "\n");
            for (LambdaDesugarer.Reason reason : LambdaDesugarer.Reason.values()) {
                out.write("kept." + reason.label() + "=" + kept.getOrDefault(reason, 0) + "\n");
            }
            out.write("kept." + LambdaDesugarer.Reason.UNRESOLVED_TYPE.label() + ".compileOnly="
                    + plan.compileOnlySites() + "\n");
            for (String note : notes) {
                out.write("note\t" + note.replace('\n', ' ').replace('\t', ' ') + "\n");
            }
            for (LambdaDesugarer.Site site : plan.sites()) {
                String outcome;
                if (site.generated() != null && fallenBack.contains(site.generated())) {
                    outcome = "kept\t" + LambdaDesugarer.Reason.NEST_FALLBACK.label();
                } else if (site.reason() != null) {
                    outcome = "kept\t" + site.reason().label() + (site.detail() == null ? "" : "\t" + site.detail());
                } else {
                    outcome = "rewritten\t" + site.generated();
                }
                out.write("site\t" + site.host() + "\t" + site.method() + "\t" + site.ordinal() + "\t" + outcome + "\n");
            }
        }
    }
}
