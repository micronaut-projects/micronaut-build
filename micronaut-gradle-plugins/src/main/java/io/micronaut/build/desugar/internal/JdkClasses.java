/*
 * Copyright 2017-2026 original authors
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
import java.io.InputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the step knows about the classes of the JDK that runs it: which packages the JDK owns, and the public
 * face of a class in one of them.
 *
 * <p>The application class loader asks its parent first for every package of a boot-layer module that the boot
 * or the platform loader defines. A class in such a package is therefore the JDK's at run time, whatever the
 * class path holds, and this class mirrors that rule with the boot layer of the JVM that runs the step. A class
 * is read from its module, which reads the runtime image, and parsed with the ClassFile API. The cache lives as
 * long as the JVM, because its JDK does not change; it holds only the classes a build asked about.</p>
 *
 * <p>Ported from Micronaut Runner's {@code JdkClasses}.</p>
 */
final class JdkClasses {

    private static final String CALLER_SENSITIVE = "Ljdk/internal/reflect/CallerSensitive;";

    /** The module of every parent-first package, keyed by the package's internal name, such as {@code java/lang}. */
    private static final Map<String, Module> MODULES = parentFirstModules();

    private static final ConcurrentHashMap<String, Optional<JdkClass>> CLASSES = new ConcurrentHashMap<>();

    private JdkClasses() {
    }

    /**
     * Whether the runtime asks the JDK first for the classes of a package.
     *
     * @param internalPackage the package, such as {@code java/util/function}, or the empty string
     * @return whether a boot-layer module of the boot or the platform loader holds the package
     */
    static boolean owns(String internalPackage) {
        return MODULES.containsKey(internalPackage);
    }

    /**
     * A class of a parent-first package.
     *
     * @param internalName the class, such as {@code java/lang/String}
     * @return the class, or {@code null} when its package is not the JDK's or the JDK has no such class
     */
    static JdkClass find(String internalName) {
        int slash = internalName.lastIndexOf('/');
        Module module = MODULES.get(slash < 0 ? "" : internalName.substring(0, slash));
        if (module == null) {
            return null;
        }
        return CLASSES.computeIfAbsent(internalName, name -> Optional.ofNullable(read(module, name))).orElse(null);
    }

    private static JdkClass read(Module module, String internalName) {
        byte[] bytes;
        try (InputStream in = module.getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                return null;
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
        try {
            ClassModel model = ClassFile.of().parse(bytes);
            Map<String, Integer> methods = new HashMap<>();
            for (MethodModel method : model.methods()) {
                int flags = method.flags().flagsMask();
                if (isCallerSensitive(method)) {
                    flags |= JdkClass.CALLER_SENSITIVE;
                }
                methods.put(method.methodName().stringValue() + method.methodType().stringValue(), flags);
            }
            List<String> interfaces = new ArrayList<>(model.interfaces().size());
            for (ClassEntry entry : model.interfaces()) {
                interfaces.add(entry.asInternalName());
            }
            int slash = internalName.lastIndexOf('/');
            String packageName = slash < 0 ? "" : internalName.substring(0, slash).replace('/', '.');
            return new JdkClass(model.flags().flagsMask(), exported(module, packageName),
                    model.superclass().map(ClassEntry::asInternalName).orElse(null), List.copyOf(interfaces),
                    Map.copyOf(methods));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isCallerSensitive(MethodModel method) {
        Optional<RuntimeVisibleAnnotationsAttribute> annotations =
                method.findAttribute(Attributes.runtimeVisibleAnnotations());
        if (annotations.isEmpty()) {
            return false;
        }
        for (Annotation annotation : annotations.get().annotations()) {
            if (annotation.className().equalsString(CALLER_SENSITIVE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a module's descriptor exports a package to everyone. The descriptor is read, not the running
     * module, so that an {@code --add-exports} of the JVM running the step does not change the output.
     */
    private static boolean exported(Module module, String packageName) {
        ModuleDescriptor descriptor = module.getDescriptor();
        if (descriptor == null) {
            return false;
        }
        for (ModuleDescriptor.Exports exports : descriptor.exports()) {
            if (!exports.isQualified() && exports.source().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Module> parentFirstModules() {
        Map<String, Module> packages = new HashMap<>(2048);
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        for (Module module : ModuleLayer.boot().modules()) {
            ClassLoader loader = module.getClassLoader();
            if (loader == null || loader == platform) {
                for (String packageName : module.getPackages()) {
                    packages.put(packageName.replace('.', '/'), module);
                }
            }
        }
        return Map.copyOf(packages);
    }

    /**
     * The public face of one JDK class.
     *
     * @param flags      its access flags
     * @param exported   whether its module exports its package to everyone
     * @param superName  its superclass's internal name, or {@code null} for {@code java/lang/Object}
     * @param interfaces the internal names of the interfaces it implements directly
     * @param methods    the flags of each method it declares, keyed by name followed by descriptor, with
     *                   {@link #CALLER_SENSITIVE} set for a caller-sensitive one
     */
    record JdkClass(int flags, boolean exported, String superName, List<String> interfaces,
                    Map<String, Integer> methods) {

        /** Set in a method's flags when it is annotated {@code @jdk.internal.reflect.CallerSensitive}. */
        static final int CALLER_SENSITIVE = 0x1_0000;

        /**
         * A method the class declares itself.
         *
         * @param name       the method's name
         * @param descriptor its descriptor
         * @return its flags, or {@code null} when the class declares no such method
         */
        Integer method(String name, String descriptor) {
            return methods.get(name + descriptor);
        }
    }
}
