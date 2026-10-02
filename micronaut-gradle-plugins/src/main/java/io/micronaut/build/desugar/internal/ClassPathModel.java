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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A read-only model of the classes a module runs with: the module's own classes first, then each entry of its
 * runtime class path in order.
 *
 * <p>It lists the class names of every root when it opens, and parses a class only when it is asked about one:
 * a module build resolves a few hundred names against class paths of tens of thousands of classes. For every
 * name it picks the copy that wins on JDK {@value #RUNTIME_FEATURE}: an earlier root before a later one, and
 * within one root the {@code META-INF/versions/N/} variants in descending version before the base entry, in a
 * jar whose manifest says {@code Multi-Release: true}. A name whose winning root holds a variant for a version
 * above {@value #RUNTIME_FEATURE} is {@linkplain #uncertain(String) uncertain}: a newer runtime would load
 * another copy.</p>
 *
 * <p>The model is a {@link ClassHierarchyResolver} for the classes it holds and falls back to the JDK's own
 * classes only. It deliberately does not use {@link ClassHierarchyResolver#defaultResolver()}, which also sees
 * the class path of the Gradle daemon. The model is confined to the thread that runs the step.</p>
 *
 * <p>Ported from Micronaut Runner's {@code ClassPathModel}, which scans eagerly.</p>
 */
final class ClassPathModel implements ClassHierarchyResolver, Closeable {

    /** The runtime feature version the winning copies are chosen for, the lowest the convention compiles for. */
    static final int RUNTIME_FEATURE = 25;

    private static final String VERSIONS_PREFIX = "META-INF/versions/";

    private static final String META_INF = "META-INF/";

    private static final String CLASS_SUFFIX = ".class";

    private static final ClassFile PARSER = ClassFile.of();

    private static final ClassHierarchyResolver JDK =
            ClassHierarchyResolver.ofResourceParsing(ClassLoader.getPlatformClassLoader()).cached(ConcurrentHashMap::new);

    private final List<Root> roots;

    /** Every copy of a name, in root order and, within a root, in the order {@link #winner(String)} tries them. */
    private final Map<String, List<Location>> locations;

    private final Map<String, Optional<Copy>> parsed = new HashMap<>();

    private ClassPathModel(List<Root> roots, Map<String, List<Location>> locations) {
        this.roots = roots;
        this.locations = locations;
    }

    /**
     * Opens a model over some roots. A root that does not exist is skipped; a root that is neither a directory
     * nor a readable jar holds no class.
     *
     * @param paths the roots, directories or jars, in class path order
     * @return the model, to close once the step is done
     * @throws IOException if a directory cannot be listed
     */
    static ClassPathModel open(List<Path> paths) throws IOException {
        List<Root> roots = new ArrayList<>(paths.size());
        Map<String, List<Location>> locations = new HashMap<>();
        try {
            for (Path path : paths) {
                Root root;
                if (Files.isDirectory(path)) {
                    root = new Root(path, null);
                } else if (Files.isRegularFile(path)) {
                    try {
                        root = new Root(path, new ZipFile(path.toFile()));
                    } catch (IOException e) {
                        continue;
                    }
                } else {
                    continue;
                }
                roots.add(root);
                list(root, roots.size() - 1, locations);
            }
        } catch (IOException | RuntimeException e) {
            for (Root root : roots) {
                root.close();
            }
            throw e;
        }
        for (List<Location> copies : locations.values()) {
            // Within one root, variants in descending version before the base entry; the sort is stable.
            copies.sort(Comparator.comparingInt(Location::root)
                    .thenComparingInt(location -> location.version == 0 ? 0 : -location.version));
        }
        return new ClassPathModel(List.copyOf(roots), locations);
    }

    private static void list(Root root, int index, Map<String, List<Location>> locations) throws IOException {
        if (root.zip == null) {
            try (Stream<Path> files = Files.walk(root.path)) {
                List<Path> classes = files.filter(file -> file.getFileName().toString().endsWith(CLASS_SUFFIX)
                        && Files.isRegularFile(file)).toList();
                for (Path file : classes) {
                    String entry = root.path.relativize(file).toString().replace('\\', '/');
                    if (!entry.startsWith(META_INF) && !isModuleInfo(entry)) {
                        add(locations, entry.substring(0, entry.length() - CLASS_SUFFIX.length()),
                                new Location(index, entry, 0));
                    }
                }
            }
            return;
        }
        boolean multiRelease = multiRelease(root.zip);
        Collections.list(root.zip.entries()).forEach(zipEntry -> {
            String entry = zipEntry.getName();
            if (zipEntry.isDirectory() || !entry.endsWith(CLASS_SUFFIX)) {
                return;
            }
            if (!entry.startsWith(META_INF)) {
                if (!isModuleInfo(entry)) {
                    add(locations, entry.substring(0, entry.length() - CLASS_SUFFIX.length()),
                            new Location(index, entry, 0));
                }
                return;
            }
            if (!multiRelease || !entry.startsWith(VERSIONS_PREFIX)) {
                return;
            }
            int slash = entry.indexOf('/', VERSIONS_PREFIX.length());
            if (slash < 0) {
                return;
            }
            int version;
            try {
                version = Integer.parseInt(entry.substring(VERSIONS_PREFIX.length(), slash));
            } catch (NumberFormatException e) {
                return;
            }
            String path = entry.substring(slash + 1);
            if (version < 9 || path.startsWith(META_INF) || isModuleInfo(path)) {
                return;
            }
            add(locations, path.substring(0, path.length() - CLASS_SUFFIX.length()), new Location(index, entry, version));
        });
    }

    private static void add(Map<String, List<Location>> locations, String name, Location location) {
        locations.computeIfAbsent(name, key -> new ArrayList<>(1)).add(location);
    }

    private static boolean multiRelease(ZipFile zip) {
        ZipEntry entry = zip.getEntry(JarFile.MANIFEST_NAME);
        if (entry == null) {
            return false;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            Manifest manifest = new Manifest(in);
            return Boolean.parseBoolean(manifest.getMainAttributes().getValue(Name.MULTI_RELEASE));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static boolean isModuleInfo(String entry) {
        return entry.equals("module-info.class") || entry.endsWith("/module-info.class");
    }

    /**
     * Whether any root holds a class of this name, whichever copy wins.
     *
     * @param internalName the class, such as {@code com/example/Foo}
     * @return whether the name is taken
     */
    boolean known(String internalName) {
        return locations.containsKey(internalName);
    }

    /**
     * The copy of a class that wins on JDK {@value #RUNTIME_FEATURE}.
     *
     * @param internalName the class, such as {@code com/example/Foo}
     * @return the winning copy, or empty when no root holds one that JDK {@value #RUNTIME_FEATURE} loads, or
     * when that copy cannot be parsed
     */
    Optional<Copy> winner(String internalName) {
        Optional<Copy> copy = parsed.get(internalName);
        if (copy == null) {
            copy = Optional.ofNullable(read(internalName));
            parsed.put(internalName, copy);
        }
        return copy;
    }

    /**
     * Whether a newer runtime would load another copy of a class than JDK {@value #RUNTIME_FEATURE} does.
     *
     * @param internalName the class
     * @return whether a variant for a version above {@value #RUNTIME_FEATURE} comes before the winner
     */
    boolean uncertain(String internalName) {
        List<Location> copies = locations.get(internalName);
        // The copies are in the order the runtime tries them: the first is the winner or comes before it.
        return copies != null && copies.get(0).version > RUNTIME_FEATURE;
    }

    /**
     * Whether code in a package can access a member of a class, by the rules the JVM applies to the winning
     * copies: the class must be public or in the same package, and the member public, or not private and in the
     * same package. A protected member of a class in another package is reported as inaccessible, because that
     * depends on the accessing class.
     *
     * @param owner       the class that declares the member
     * @param name        the member's name
     * @param descriptor  the member's descriptor
     * @param fromPackage the accessing package, such as {@code com/example}
     * @return whether the access is legal; {@code false} when the model has no such class or member
     */
    boolean isAccessible(String owner, String name, String descriptor, String fromPackage) {
        Optional<Copy> copy = winner(owner);
        if (copy.isEmpty()) {
            return false;
        }
        Member member = copy.get().member(name, descriptor);
        if (member == null) {
            return false;
        }
        boolean samePackage = packageOf(owner).equals(fromPackage);
        if ((copy.get().flags & ClassFile.ACC_PUBLIC) == 0 && !samePackage) {
            return false;
        }
        if ((member.flags & ClassFile.ACC_PUBLIC) != 0) {
            return true;
        }
        return (member.flags & ClassFile.ACC_PRIVATE) == 0 && samePackage;
    }

    @Override
    public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
        if (classDesc.isClassOrInterface()) {
            String descriptor = classDesc.descriptorString();
            Optional<Copy> copy = winner(descriptor.substring(1, descriptor.length() - 1));
            if (copy.isPresent()) {
                if (copy.get().isInterface()) {
                    return ClassHierarchyInfo.ofInterface();
                }
                return ClassHierarchyInfo.ofClass(copy.get().superName == null
                        ? null : ClassDesc.ofInternalName(copy.get().superName));
            }
        }
        return JDK.getClassInfo(classDesc);
    }

    @Override
    public void close() {
        for (Root root : roots) {
            root.close();
        }
    }

    /** The copy that wins: the first, in root order, that JDK {@value #RUNTIME_FEATURE} loads. */
    private static Location location(List<Location> copies) {
        for (Location location : copies) {
            if (location.version <= RUNTIME_FEATURE) {
                return location;
            }
        }
        return null;
    }

    private Copy read(String internalName) {
        List<Location> copies = locations.get(internalName);
        if (copies == null) {
            return null;
        }
        Location location = location(copies);
        if (location == null) {
            return null;
        }
        Root root = roots.get(location.root);
        byte[] bytes;
        try {
            bytes = root.read(location.entry);
        } catch (IOException e) {
            return null;
        }
        if (bytes == null) {
            return null;
        }
        try {
            ClassModel model = PARSER.parse(bytes);
            if (!model.thisClass().asInternalName().equals(internalName)) {
                return null;
            }
            List<String> interfaces = new ArrayList<>(model.interfaces().size());
            for (ClassEntry entry : model.interfaces()) {
                interfaces.add(entry.asInternalName());
            }
            List<Member> members = new ArrayList<>(model.fields().size() + model.methods().size());
            for (FieldModel field : model.fields()) {
                members.add(new Member(field.fieldName().stringValue(), field.fieldType().stringValue(),
                        field.flags().flagsMask()));
            }
            for (MethodModel method : model.methods()) {
                members.add(new Member(method.methodName().stringValue(), method.methodType().stringValue(),
                        method.flags().flagsMask()));
            }
            String nestHost = model.findAttribute(Attributes.nestHost())
                    .map(attribute -> attribute.nestHost().asInternalName()).orElse(null);
            return new Copy(location.root, location.version, model.flags().flagsMask(),
                    model.superclass().map(ClassEntry::asInternalName).orElse(null), List.copyOf(interfaces),
                    List.copyOf(members), nestHost);
        } catch (IllegalArgumentException e) {
            // The ClassFile API reads lazily: a malformed class only fails when a part of it is asked for.
            return null;
        }
    }

    private static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    /**
     * One field or method of a class.
     *
     * @param name       its name
     * @param descriptor its descriptor
     * @param flags      its access flags
     */
    record Member(String name, String descriptor, int flags) {
    }

    /**
     * Where one copy of a class lives.
     *
     * @param root    the position of its root
     * @param entry   its path in the root
     * @param version {@code N} of {@code META-INF/versions/N/}, or {@code 0} for the base entry
     */
    private record Location(int root, String entry, int version) {
    }

    /**
     * One copy of a class, as one root holds it.
     *
     * @param root       the position of its root: {@code 0} for the module's own classes
     * @param version    the version directory it lives in, {@code 0} for the base entry
     * @param flags      its access flags
     * @param superName  its superclass, or {@code null} for {@code java/lang/Object}
     * @param interfaces the interfaces it implements directly
     * @param members    the fields and methods it declares
     * @param nestHost   the class its {@code NestHost} attribute names, or {@code null}
     */
    record Copy(int root, int version, int flags, String superName, List<String> interfaces, List<Member> members,
                String nestHost) {

        boolean isInterface() {
            return (flags & ClassFile.ACC_INTERFACE) != 0;
        }

        /**
         * A field or method the class declares itself.
         *
         * @param name       the member's name
         * @param descriptor its descriptor
         * @return the member, or {@code null} when the class declares none
         */
        Member member(String name, String descriptor) {
            for (Member member : members) {
                if (member.name.equals(name) && member.descriptor.equals(descriptor)) {
                    return member;
                }
            }
            return null;
        }
    }

    /** A directory or a jar of the class path. */
    private record Root(Path path, ZipFile zip) {

        byte[] read(String entry) throws IOException {
            if (zip == null) {
                Path file = path.resolve(entry);
                return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
            }
            ZipEntry zipEntry = zip.getEntry(entry);
            if (zipEntry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(zipEntry)) {
                return in.readAllBytes();
            }
        }

        void close() {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                    // Nothing was written to it.
                }
            }
        }
    }
}
