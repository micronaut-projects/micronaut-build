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
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.attribute.NestMembersAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plans the desugaring of a module's lambda and method-reference call sites, and writes the classes that replace
 * them.
 *
 * <p>A lambda compiles to an {@code invokedynamic} whose bootstrap, {@code LambdaMetafactory.metafactory}, spins
 * a hidden class the first time the site runs. This step writes that class ahead of time, as an ordinary class
 * next to its host, and points the site at it, so that the runtime links nothing.</p>
 *
 * <h2>What a site becomes</h2>
 * <p>One class per site, {@code <Host>$$Lambda$R<n>}: package-private, final and synthetic, with the host's
 * class-file version and {@code SourceFile}, in the host's package, and a member of the host's nest. It
 * implements the site's functional interface, keeps the captured values in final fields, and forwards the
 * interface method to the implementation with the argument and return conversions {@code LambdaMetafactory}
 * would apply. Its methods are synthetic too, so that a debugger that skips synthetic methods steps straight
 * into the lambda body, which stays where the compiler put it. The site itself becomes
 * {@code invokestatic <Host>$$Lambda$R<n>.create}, with the descriptor of the {@code invokedynamic}, followed by
 * two {@code nop}s: the same length and the same stack effect, so every bytecode offset of the method and its
 * frames stay valid. A site that captures nothing always yields the same instance. No member of a host is added,
 * removed or changed: only the nest host's {@code NestMembers} attribute grows.</p>
 *
 * <h2>What stays {@code invokedynamic}</h2>
 * <p>A site is rewritten only when the generated class provably resolves what the site resolved; every
 * {@link Reason} names one way it does not. Neither {@code altMetafactory} (serializable and marker-interface
 * lambdas) nor any other bootstrap is ever rewritten, and neither is a host below class-file version 55, which
 * has no nestmates.</p>
 *
 * <p>A module build adds one rule to Micronaut Runner's, which resolves against the class path the application
 * runs with: every type the generated class names must resolve against the module's own classes, its runtime
 * class path or the JDK. A type found only on the compile class path, through a {@code compileOnly} dependency,
 * may be absent at run time, and the site then fails at the same point, with the same error, as it does
 * today.</p>
 *
 * <p>The output is deterministic for a given JDK: hosts are planned in entry order, and sites are numbered per
 * host in method order and then bytecode order. Ported from Micronaut Runner's {@code LambdaDesugarer}.</p>
 */
final class LambdaDesugarer {

    /** What follows the host's name in a generated class's name, before the site number. */
    static final String GENERATED_INFIX = "$$Lambda$R";

    /** The static method of a generated class that a rewritten site calls. */
    static final String FACTORY_METHOD = "create";

    /** The constant pool string every class with a lambda call site holds. */
    private static final byte[] MARKER = "java/lang/invoke/LambdaMetafactory".getBytes(StandardCharsets.UTF_8);

    private static final String METAFACTORY_OWNER = "java/lang/invoke/LambdaMetafactory";

    private static final String METAFACTORY = "metafactory";

    private static final String ALT_METAFACTORY = "altMetafactory";

    private static final String CLASS_SUFFIX = ".class";

    private static final String INSTANCE_FIELD = "INSTANCE";

    private static final String CONSTRUCTOR = ConstantDescs.INIT_NAME;

    /** The first class-file version with {@code invokedynamic}. */
    private static final int INDY_MAJOR = 51;

    /** The first class-file version with nestmates. */
    private static final int NESTMATE_MAJOR = 55;

    private static final int CLASS_MAGIC = 0xCAFEBABE;

    private static final int UNRESOLVED = 0;

    private static final int CLASS = 1;

    private static final int INTERFACE = 2;

    private static final ClassFile PARSER = ClassFile.of();

    /** Generated classes have no branch, so they need no frames, and no class hierarchy to compute any. */
    private static final ClassFile GENERATOR = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);

    private final ClassPathModel model;

    private final ClassPathModel compileClasspath;

    private final ClassReader classes;

    /**
     * A desugarer over a module.
     *
     * @param model            the module's classes, as root 0, and its runtime class path
     * @param compileClasspath the module's compile class path, which tells which unresolved types come from a
     *                         compile-only dependency
     * @param classes          reads a class of the module by entry name
     */
    LambdaDesugarer(ClassPathModel model, ClassPathModel compileClasspath, ClassReader classes) {
        this.model = model;
        this.compileClasspath = compileClasspath;
        this.classes = classes;
    }

    /**
     * The pre-filter: a class file of a version that has {@code invokedynamic} that names {@code LambdaMetafactory}.
     *
     * @param bytes the class bytes
     * @return whether the class may hold a lambda call site
     */
    static boolean matches(byte[] bytes) {
        return bytes.length > 8 && u4(bytes, 0) == CLASS_MAGIC && u2(bytes, 6) >= INDY_MAJOR && contains(bytes, MARKER);
    }

    private static boolean contains(byte[] bytes, byte[] marker) {
        outer:
        for (int i = 0; i <= bytes.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (bytes[i + j] != marker[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Plans the module: decides which sites are rewritten and allocates the generated names.
     *
     * @param hosts the classes that pass the pre-filter, keyed by entry name, in entry order
     * @return the plan
     * @throws IOException if a nest host cannot be read
     */
    Plan plan(Map<String, byte[]> hosts) throws IOException {
        Planner planner = new Planner();
        for (Map.Entry<String, byte[]> host : hosts.entrySet()) {
            planner.host(host.getKey(), host.getValue());
        }
        return planner.finish();
    }

    private static int u2(byte[] bytes, int position) {
        return (bytes[position] & 0xFF) << 8 | bytes[position + 1] & 0xFF;
    }

    private static int u4(byte[] bytes, int position) {
        return u2(bytes, position) << 16 | u2(bytes, position + 2);
    }

    private static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    private static String internalName(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1);
    }

    private static String key(MethodModel method) {
        return method.methodName().stringValue() + method.methodType().stringValue();
    }

    private static boolean isVoid(ClassDesc type) {
        return type.descriptorString().equals("V");
    }

    /**
     * What a name resolves to at run time, as far as the build can tell: for a package the JDK owns, the JDK's
     * class; otherwise the winning copy of the module and its runtime class path.
     *
     * @return {@link #UNRESOLVED}, {@link #CLASS} or {@link #INTERFACE}
     */
    private int kindOf(String internalName) {
        int flags;
        if (JdkClasses.owns(packageOf(internalName))) {
            JdkClasses.JdkClass jdk = JdkClasses.find(internalName);
            if (jdk == null) {
                return UNRESOLVED;
            }
            flags = jdk.flags();
        } else {
            Optional<ClassPathModel.Copy> copy = model.winner(internalName);
            if (copy.isEmpty()) {
                return UNRESOLVED;
            }
            flags = copy.get().flags();
        }
        return (flags & ClassFile.ACC_INTERFACE) != 0 ? INTERFACE : CLASS;
    }

    /**
     * The class a type names, possibly as the element type of an array, if it does not resolve.
     *
     * @return the class's internal name, or {@code null} when the type is a primitive, {@code void} or a class
     * {@link #kindOf(String)} finds
     */
    private String unresolved(ClassDesc type) {
        ClassDesc element = type;
        while (element.isArray()) {
            element = element.componentType();
        }
        if (element.isPrimitive()) {
            return null;
        }
        String name = internalName(element);
        return kindOf(name) == UNRESOLVED ? name : null;
    }

    /**
     * Whether any {@code invokedynamic} constant of a class is bootstrapped by {@code LambdaMetafactory}: the
     * marker alone may be a string the class merely mentions.
     */
    private static boolean usesMetafactory(ClassModel model) {
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof InvokeDynamicEntry indy) {
                MemberRefEntry bootstrap = indy.bootstrap().bootstrapMethod().reference();
                if (bootstrap.owner().name().equalsString(METAFACTORY_OWNER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The {@code LambdaMetafactory} bootstrap a site uses.
     *
     * @return the bootstrap method's name, or {@code null} for any other bootstrap
     */
    private static String bootstrap(InvokeDynamicInstruction indy) {
        MemberRefEntry bootstrap = indy.invokedynamic().bootstrap().bootstrapMethod().reference();
        if (!bootstrap.owner().name().equalsString(METAFACTORY_OWNER)) {
            return null;
        }
        return bootstrap.name().stringValue();
    }

    private static List<ClassDesc> nestMembers(ClassModel model) {
        Optional<NestMembersAttribute> attribute = model.findAttribute(Attributes.nestMembers());
        if (attribute.isEmpty()) {
            return List.of();
        }
        List<ClassDesc> members = new ArrayList<>(attribute.get().nestMembers().size());
        for (ClassEntry member : attribute.get().nestMembers()) {
            members.add(member.asSymbol());
        }
        return members;
    }

    /**
     * Reads a class of the module.
     */
    @FunctionalInterface
    interface ClassReader {

        /**
         * Reads a class.
         *
         * @param entryName the class's entry name, such as {@code com/example/Foo.class}
         * @return its bytes, or {@code null} when the module has no such class
         * @throws IOException if the class cannot be read
         */
        byte[] read(String entryName) throws IOException;
    }

    /**
     * Why a {@code metafactory} site stays {@code invokedynamic}.
     */
    enum Reason {

        /** The bootstrap is {@code altMetafactory}: a serializable, marker-interface or bridged lambda. */
        ALT_METAFACTORY("altMetafactory"),

        /** The host is below class-file version 55, which has no nestmates. */
        CLASS_VERSION("classVersion"),

        /** The host's nest host is not a class of the module that lists the host as a member. */
        NEST("nest"),

        /** The implementation's owner is a dependency class whose copy a newer runtime may replace. */
        SHADOWED_OR_UNCERTAIN("shadowedOrUncertain"),

        /**
         * The implementation is an {@code invokespecial} that stays non-virtual: on another class, such as
         * {@code super::method}, or of a member of the host that is not private.
         */
        SUPER_CALL("superCall"),

        /**
         * The implementation's owner does not declare the member with that descriptor, or the generated class
         * could not access it.
         */
        OWNER_ACCESS("ownerAccess"),

        /** The implementation is a caller-sensitive method of the JDK. */
        CALLER_SENSITIVE("callerSensitive"),

        /**
         * A class the generated class would name does not resolve against the module, its runtime class path or
         * the JDK, for instance because it comes from a compile-only dependency.
         */
        UNRESOLVED_TYPE("unresolvedType"),

        /** A class already has the name the generated class would take. */
        NAME_TAKEN("nameTaken"),

        /** The site has a shape {@code LambdaMetafactory} would reject, or one this step does not generate. */
        SHAPE("shape"),

        /** The site's nest was planned, and then kept as compiled because its rewrite did not verify. */
        NEST_FALLBACK("nestFallback");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        /**
         * The reason as the report spells it.
         *
         * @return the label
         */
        String label() {
            return label;
        }
    }

    /**
     * What happened to one call site, for the report.
     *
     * @param host      the host's internal name
     * @param method    the method that holds the site, by name and descriptor
     * @param ordinal   the site's position among the {@code invokedynamic} instructions of that method
     * @param generated the generated class's internal name, or {@code null} when the site is kept
     * @param reason    why the site is kept, or {@code null} when it is rewritten
     * @param detail    what the reason is about, such as the type that does not resolve, or {@code null}
     */
    record Site(String host, String method, int ordinal, String generated, Reason reason, String detail) {
    }

    /**
     * The plan of a module.
     *
     * @param units                the nests to rewrite, in the order their first host appears
     * @param sites                every {@code LambdaMetafactory} site, in host, method and bytecode order
     * @param compileOnlySites     the {@link Reason#UNRESOLVED_TYPE} sites whose type is on the compile class path
     */
    record Plan(List<Unit> units, List<Site> sites, int compileOnlySites) {
    }

    /**
     * One nest to rewrite: the nest host, the members that hold rewritten sites, and their generated classes.
     */
    static final class Unit {

        private final String nestHostEntry;
        private final Map<String, ClassPlan> classes = new LinkedHashMap<>();

        private Unit(String nestHostEntry) {
            this.nestHostEntry = nestHostEntry;
        }

        /**
         * The entry of the nest host, which names the unit.
         *
         * @return the entry name
         */
        String nestHostEntry() {
            return nestHostEntry;
        }

        /**
         * The existing classes the unit rewrites, keyed by entry name.
         *
         * @return the classes
         */
        Map<String, ClassPlan> classes() {
            return classes;
        }

        /**
         * The internal names of the classes the unit generates.
         *
         * @return the names, in site order
         */
        List<String> generatedNames() {
            List<String> names = new ArrayList<>();
            for (ClassPlan plan : classes.values()) {
                for (SitePlan site : plan.sites) {
                    names.add(internalName(site.generated));
                }
            }
            return names;
        }

        /**
         * Generates the unit's classes.
         *
         * @return the generated classes, keyed by entry name, in site order
         */
        Map<String, byte[]> generate() {
            Map<String, byte[]> generated = new LinkedHashMap<>();
            for (ClassPlan plan : classes.values()) {
                for (SitePlan site : plan.sites) {
                    generated.put(internalName(site.generated) + CLASS_SUFFIX, site.generate());
                }
            }
            return generated;
        }
    }

    /**
     * What the step does to one existing class: the sites it rewrites and, for a nest host, the members it gains.
     */
    static final class ClassPlan {

        private final byte[] original;
        private final Map<String, SitePlan[]> sitesByMethod;
        private final List<SitePlan> sites;
        private List<ClassDesc> nestMembers = List.of();

        private ClassPlan(byte[] original, Map<String, SitePlan[]> sitesByMethod, List<SitePlan> sites) {
            this.original = original;
            this.sitesByMethod = sitesByMethod;
            this.sites = sites;
        }

        /**
         * The class as the compiler wrote it.
         *
         * @return its bytes
         */
        byte[] original() {
            return original;
        }

        /**
         * The transform that rewrites the class. Every edit keeps its length and its stack effect.
         *
         * @return a fresh transform
         */
        ClassTransform transform() {
            return new ClassTransform() {
                @Override
                public void accept(ClassBuilder builder, ClassElement element) {
                    if (element instanceof NestMembersAttribute && !nestMembers.isEmpty()) {
                        // Written again at the end, with the generated classes.
                        return;
                    }
                    if (element instanceof MethodModel method && method.code().isPresent()) {
                        SitePlan[] planned = sitesByMethod.get(key(method));
                        if (planned != null) {
                            builder.transformMethod(method, MethodTransform.transformingCode(new Rewriter(planned)));
                            return;
                        }
                    }
                    builder.with(element);
                }

                @Override
                public void atEnd(ClassBuilder builder) {
                    if (!nestMembers.isEmpty()) {
                        builder.with(NestMembersAttribute.ofSymbols(nestMembers));
                    }
                }
            };
        }
    }

    /**
     * Rewrites the planned sites of one method. A site is found by its position among the method's
     * {@code invokedynamic} instructions.
     */
    private static final class Rewriter implements CodeTransform {

        private final SitePlan[] planned;
        private int ordinal;

        private Rewriter(SitePlan[] planned) {
            this.planned = planned;
        }

        @Override
        public void atStart(CodeBuilder builder) {
            // The ClassFile API may run a code handler again, for instance to widen a jump.
            ordinal = 0;
        }

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            if (element instanceof InvokeDynamicInstruction indy) {
                SitePlan site = ordinal < planned.length ? planned[ordinal] : null;
                ordinal++;
                if (site != null) {
                    if (!indy.typeSymbol().equals(site.factoryType) || !indy.name().equalsString(site.samName)) {
                        throw new IllegalStateException("The call site of " + site.generated.displayName()
                                + " is not where it was planned");
                    }
                    // 3 + 1 + 1 bytes, as the invokedynamic, with the same stack effect.
                    builder.invokestatic(site.generated, FACTORY_METHOD, site.factoryType).nop().nop();
                    return;
                }
            }
            builder.with(element);
        }
    }

    /** How a generated class calls the implementation. */
    private enum Invocation {
        STATIC, VIRTUAL, INTERFACE, CONSTRUCTOR
    }

    /**
     * Where the classes generated for a host go.
     *
     * @param major      the host's class-file version, which its generated classes take
     * @param minor      the host's minor version
     * @param nestHost   the nest the generated classes join
     * @param sourceFile the host's {@code SourceFile}, or {@code null}
     */
    private record Home(int major, int minor, ClassDesc nestHost, String sourceFile) {
    }

    /**
     * A call site, as the host's code states it.
     *
     * @param factoryType      the descriptor of the {@code invokedynamic}: the captured types to the functional
     *                         interface
     * @param samName          the name of the interface method
     * @param samType          the erased descriptor of the interface method, which the generated class declares
     * @param instantiatedType the descriptor the interface method has at this site, which decides the casts
     */
    private record Shape(MethodTypeDesc factoryType, String samName, MethodTypeDesc samType,
                         MethodTypeDesc instantiatedType) {
    }

    /**
     * The implementation a site forwards to.
     *
     * @param owner          the class that declares it
     * @param ownerInterface whether the site names that class as an interface
     * @param name           its name, {@code <init>} for a constructor
     * @param descriptor     its descriptor, as its owner declares it
     * @param type           the implementation as a call: the receiver first for an instance method, the owner as
     *                       a constructor's result
     * @param invocation     how a generated class calls it
     */
    private record Target(ClassDesc owner, boolean ownerInterface, String name, MethodTypeDesc descriptor,
                          MethodTypeDesc type, Invocation invocation) {
    }

    /**
     * One rewritten site: everything its generated class is written from.
     */
    private static final class SitePlan {

        private final Home home;
        private final ClassDesc generated;
        private final MethodTypeDesc factoryType;
        private final String samName;
        private final MethodTypeDesc samType;
        private final MethodTypeDesc instantiatedType;
        private final Target target;

        private SitePlan(Home home, ClassDesc generated, Shape shape, Target target) {
            this.home = home;
            this.generated = generated;
            this.factoryType = shape.factoryType();
            this.samName = shape.samName();
            this.samType = shape.samType();
            this.instantiatedType = shape.instantiatedType();
            this.target = target;
        }

        /**
         * Writes the generated class.
         */
        private byte[] generate() {
            int captured = factoryType.parameterCount();
            ClassDesc functionalInterface = factoryType.returnType();
            MethodTypeDesc constructorType = MethodTypeDesc.of(ConstantDescs.CD_void, factoryType.parameterList());
            return GENERATOR.build(generated, builder -> {
                builder.withVersion(home.major(), home.minor());
                builder.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC | ClassFile.ACC_SUPER);
                builder.withSuperclass(ConstantDescs.CD_Object);
                builder.withInterfaceSymbols(functionalInterface);
                if (captured == 0) {
                    builder.withField(INSTANCE_FIELD, generated,
                            ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC);
                    builder.withMethodBody(ConstantDescs.CLASS_INIT_NAME, ConstantDescs.MTD_void,
                            ClassFile.ACC_STATIC, code -> code
                                    .new_(generated).dup()
                                    .invokespecial(generated, CONSTRUCTOR, constructorType)
                                    .putstatic(generated, INSTANCE_FIELD, generated)
                                    .return_());
                }
                for (int i = 0; i < captured; i++) {
                    builder.withField("f" + i, factoryType.parameterType(i),
                            ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC);
                }
                builder.withMethodBody(CONSTRUCTOR, constructorType, ClassFile.ACC_PRIVATE | ClassFile.ACC_SYNTHETIC,
                        code -> {
                            code.aload(0).invokespecial(ConstantDescs.CD_Object, CONSTRUCTOR, ConstantDescs.MTD_void);
                            int slot = 1;
                            for (int i = 0; i < captured; i++) {
                                ClassDesc type = factoryType.parameterType(i);
                                TypeKind kind = TypeKind.from(type);
                                code.aload(0).loadLocal(kind, slot).putfield(generated, "f" + i, type);
                                slot += kind.slotSize();
                            }
                            code.return_();
                        });
                builder.withMethodBody(FACTORY_METHOD, factoryType, ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC,
                        code -> {
                            if (captured == 0) {
                                code.getstatic(generated, INSTANCE_FIELD, generated).areturn();
                                return;
                            }
                            code.new_(generated).dup();
                            int slot = 0;
                            for (int i = 0; i < captured; i++) {
                                TypeKind kind = TypeKind.from(factoryType.parameterType(i));
                                code.loadLocal(kind, slot);
                                slot += kind.slotSize();
                            }
                            code.invokespecial(generated, CONSTRUCTOR, constructorType).areturn();
                        });
                builder.withMethodBody(samName, samType, ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC,
                        this::forward);
                builder.with(NestHostAttribute.of(home.nestHost()));
                if (home.sourceFile() != null) {
                    builder.with(SourceFileAttribute.of(home.sourceFile()));
                }
            });
        }

        /**
         * The body of the interface method: the captured values, then each argument adapted to the
         * implementation, the call, and the result adapted back.
         */
        private void forward(CodeBuilder code) {
            int captured = factoryType.parameterCount();
            MethodTypeDesc implType = target.type();
            if (target.invocation() == Invocation.CONSTRUCTOR) {
                code.new_(target.owner()).dup();
            }
            for (int i = 0; i < captured; i++) {
                code.aload(0).getfield(generated, "f" + i, factoryType.parameterType(i));
            }
            int slot = 1;
            for (int i = 0; i < samType.parameterCount(); i++) {
                ClassDesc argument = samType.parameterType(i);
                TypeKind kind = TypeKind.from(argument);
                code.loadLocal(kind, slot);
                slot += kind.slotSize();
                Conversions.convert(code, argument, implType.parameterType(captured + i),
                        instantiatedType.parameterType(i));
            }
            switch (target.invocation()) {
                case STATIC -> code.invokestatic(target.owner(), target.name(), target.descriptor(),
                        target.ownerInterface());
                case VIRTUAL -> code.invokevirtual(target.owner(), target.name(), target.descriptor());
                case INTERFACE -> code.invokeinterface(target.owner(), target.name(), target.descriptor());
                case CONSTRUCTOR -> code.invokespecial(target.owner(), target.name(), target.descriptor());
                default -> throw new IllegalStateException("Unknown invocation " + target.invocation());
            }
            ClassDesc result = implType.returnType();
            ClassDesc expected = samType.returnType();
            if (isVoid(expected)) {
                if (!isVoid(result)) {
                    if (TypeKind.from(result).slotSize() == 2) {
                        code.pop2();
                    } else {
                        code.pop();
                    }
                }
                code.return_();
                return;
            }
            Conversions.convert(code, result, expected, expected);
            code.return_(TypeKind.from(expected));
        }
    }

    /**
     * The argument and return conversions of {@code LambdaMetafactory}, from
     * {@code java.lang.invoke.TypeConvertingMethodAdapter}, over type descriptors instead of loaded classes.
     */
    private static final class Conversions {

        private Conversions() {
        }

        /**
         * Converts the value on top of the stack.
         *
         * @param code       where to emit
         * @param argument   the type the value has
         * @param target     the type it must get
         * @param functional the type the site says it has, which is cast to first when it is more specific
         */
        static void convert(CodeBuilder code, ClassDesc argument, ClassDesc target, ClassDesc functional) {
            if (argument.equals(target) && argument.equals(functional)) {
                return;
            }
            if (isVoid(argument) || isVoid(target)) {
                return;
            }
            if (argument.isPrimitive()) {
                if (target.isPrimitive()) {
                    widen(code, TypeKind.from(argument), TypeKind.from(target));
                    return;
                }
                TypeKind unwrapped = unwrapped(target);
                if (unwrapped != null) {
                    widen(code, TypeKind.from(argument), unwrapped);
                    box(code, unwrapped);
                } else {
                    box(code, TypeKind.from(argument));
                    cast(code, target);
                }
                return;
            }
            ClassDesc source;
            if (argument.equals(functional) || functional.isPrimitive()) {
                source = argument;
            } else {
                source = functional;
                cast(code, functional);
            }
            if (!target.isPrimitive()) {
                if (!source.equals(target)) {
                    cast(code, target);
                }
                return;
            }
            TypeKind kind = TypeKind.from(target);
            TypeKind unwrapped = unwrapped(source);
            if (unwrapped != null) {
                unbox(code, wrapper(unwrapped), unwrapped);
                widen(code, unwrapped, kind);
            } else if (kind == TypeKind.BOOLEAN || kind == TypeKind.CHAR) {
                code.checkcast(wrapper(kind));
                unbox(code, wrapper(kind), kind);
            } else {
                code.checkcast(ConstantDescs.CD_Number);
                unbox(code, ConstantDescs.CD_Number, kind);
            }
        }

        /**
         * Whether {@link #convert} has a legal conversion for a value, as far as descriptors alone can tell.
         * Two reference types are taken to be related, as {@code javac} guarantees.
         */
        static boolean convertible(ClassDesc argument, ClassDesc target, ClassDesc functional) {
            if (isVoid(argument) || isVoid(target) || isVoid(functional)) {
                return false;
            }
            if (argument.isPrimitive()) {
                if (target.isPrimitive()) {
                    return widens(TypeKind.from(argument), TypeKind.from(target));
                }
                TypeKind unwrapped = unwrapped(target);
                return unwrapped == null || widens(TypeKind.from(argument), unwrapped);
            }
            if (!target.isPrimitive()) {
                return true;
            }
            ClassDesc source = argument.equals(functional) || functional.isPrimitive() ? argument : functional;
            TypeKind unwrapped = unwrapped(source);
            return unwrapped == null || widens(unwrapped, TypeKind.from(target));
        }

        private static boolean widens(TypeKind from, TypeKind to) {
            if (from == to) {
                return true;
            }
            return switch (from) {
                case BYTE -> to == TypeKind.SHORT || to == TypeKind.INT || to == TypeKind.LONG
                        || to == TypeKind.FLOAT || to == TypeKind.DOUBLE;
                case SHORT, CHAR -> to == TypeKind.INT || to == TypeKind.LONG || to == TypeKind.FLOAT
                        || to == TypeKind.DOUBLE;
                case INT -> to == TypeKind.LONG || to == TypeKind.FLOAT || to == TypeKind.DOUBLE;
                case LONG -> to == TypeKind.FLOAT || to == TypeKind.DOUBLE;
                case FLOAT -> to == TypeKind.DOUBLE;
                default -> false;
            };
        }

        private static void widen(CodeBuilder code, TypeKind from, TypeKind to) {
            TypeKind source = from.asLoadable();
            TypeKind destination = to.asLoadable();
            if (source == destination) {
                return;
            }
            switch (source) {
                case INT -> {
                    switch (destination) {
                        case LONG -> code.i2l();
                        case FLOAT -> code.i2f();
                        case DOUBLE -> code.i2d();
                        default -> throw new IllegalStateException("No widening from int to " + destination);
                    }
                }
                case LONG -> {
                    switch (destination) {
                        case FLOAT -> code.l2f();
                        case DOUBLE -> code.l2d();
                        default -> throw new IllegalStateException("No widening from long to " + destination);
                    }
                }
                case FLOAT -> {
                    if (destination != TypeKind.DOUBLE) {
                        throw new IllegalStateException("No widening from float to " + destination);
                    }
                    code.f2d();
                }
                default -> throw new IllegalStateException("No widening from " + source + " to " + destination);
            }
        }

        private static void cast(CodeBuilder code, ClassDesc target) {
            if (!target.equals(ConstantDescs.CD_Object)) {
                code.checkcast(target);
            }
        }

        private static void box(CodeBuilder code, TypeKind kind) {
            ClassDesc wrapper = wrapper(kind);
            code.invokestatic(wrapper, "valueOf", MethodTypeDesc.of(wrapper, primitive(kind)));
        }

        private static void unbox(CodeBuilder code, ClassDesc owner, TypeKind kind) {
            ClassDesc primitive = primitive(kind);
            code.invokevirtual(owner, primitive.displayName() + "Value", MethodTypeDesc.of(primitive));
        }

        /** The primitive a wrapper class wraps, or {@code null} for any other type. */
        private static TypeKind unwrapped(ClassDesc type) {
            return switch (type.descriptorString()) {
                case "Ljava/lang/Boolean;" -> TypeKind.BOOLEAN;
                case "Ljava/lang/Byte;" -> TypeKind.BYTE;
                case "Ljava/lang/Short;" -> TypeKind.SHORT;
                case "Ljava/lang/Character;" -> TypeKind.CHAR;
                case "Ljava/lang/Integer;" -> TypeKind.INT;
                case "Ljava/lang/Long;" -> TypeKind.LONG;
                case "Ljava/lang/Float;" -> TypeKind.FLOAT;
                case "Ljava/lang/Double;" -> TypeKind.DOUBLE;
                default -> null;
            };
        }

        private static ClassDesc wrapper(TypeKind kind) {
            return switch (kind) {
                case BOOLEAN -> ConstantDescs.CD_Boolean;
                case BYTE -> ConstantDescs.CD_Byte;
                case SHORT -> ConstantDescs.CD_Short;
                case CHAR -> ConstantDescs.CD_Character;
                case INT -> ConstantDescs.CD_Integer;
                case LONG -> ConstantDescs.CD_Long;
                case FLOAT -> ConstantDescs.CD_Float;
                case DOUBLE -> ConstantDescs.CD_Double;
                default -> throw new IllegalStateException("No wrapper for " + kind);
            };
        }

        private static ClassDesc primitive(TypeKind kind) {
            return switch (kind) {
                case BOOLEAN -> ConstantDescs.CD_boolean;
                case BYTE -> ConstantDescs.CD_byte;
                case SHORT -> ConstantDescs.CD_short;
                case CHAR -> ConstantDescs.CD_char;
                case INT -> ConstantDescs.CD_int;
                case LONG -> ConstantDescs.CD_long;
                case FLOAT -> ConstantDescs.CD_float;
                case DOUBLE -> ConstantDescs.CD_double;
                default -> throw new IllegalStateException("No primitive for " + kind);
            };
        }
    }

    /**
     * A nest host, as the module holds it.
     */
    private static final class Nest {

        private final String name;
        private final byte[] bytes;
        /** The members its {@code NestMembers} attribute lists, in order. */
        private final List<ClassDesc> members;
        private final Set<String> memberNames;

        private Nest(String name, byte[] bytes, List<ClassDesc> members) {
            this.name = name;
            this.bytes = bytes;
            this.members = members;
            this.memberNames = new HashSet<>();
            for (ClassDesc member : members) {
                memberNames.add(internalName(member));
            }
        }

        private boolean holds(String internalName) {
            return internalName.equals(name) || memberNames.contains(internalName);
        }
    }

    /**
     * One {@code metafactory} site, as the host's code holds it.
     *
     * @param method  the method that holds it, by name and descriptor
     * @param ordinal its position among the {@code invokedynamic} instructions of that method
     * @param indy    the instruction
     */
    private record Found(String method, int ordinal, InvokeDynamicInstruction indy) {
    }

    /**
     * Why a site is kept, and what about.
     *
     * @param reason the reason
     * @param detail the type that does not resolve, or {@code null}
     */
    private record Kept(Reason reason, String detail) {
    }

    /**
     * Plans the module.
     */
    private final class Planner {

        /** The nest hosts read so far, by internal name; {@code null} for one that cannot be used. */
        private final Map<String, Nest> nests = new HashMap<>();
        private final Map<String, Unit> units = new LinkedHashMap<>();
        private final Map<String, Nest> unitNests = new HashMap<>();
        private final List<Site> sites = new ArrayList<>();
        private int compileOnly;

        /**
         * Plans one class that passed the pre-filter. A class that cannot be parsed is not planned.
         */
        private void host(String entryName, byte[] bytes) throws IOException {
            List<Site> own = new ArrayList<>();
            HostPlan plan;
            try {
                plan = evaluate(entryName, bytes, own);
            } catch (RuntimeException e) {
                return;
            }
            sites.addAll(own);
            for (Site site : own) {
                if (site.reason() == Reason.UNRESOLVED_TYPE && site.detail() != null
                        && compileClasspath.known(site.detail())) {
                    compileOnly++;
                }
            }
            if (plan == null || plan.sites.isEmpty()) {
                return;
            }
            Unit unit = units.computeIfAbsent(plan.nest.name, name -> new Unit(name + CLASS_SUFFIX));
            unitNests.put(plan.nest.name, plan.nest);
            unit.classes.put(entryName, new ClassPlan(bytes, plan.sitesByMethod, plan.sites));
        }

        /**
         * Completes the plan: every nest host is rewritten, even without a site of its own, to list the generated
         * classes of its nest after the members it already has.
         */
        private Plan finish() {
            List<Unit> planned = new ArrayList<>(units.size());
            for (Map.Entry<String, Unit> entry : units.entrySet()) {
                Unit unit = entry.getValue();
                Nest nest = unitNests.get(entry.getKey());
                List<ClassDesc> members = new ArrayList<>(nest.members);
                for (ClassPlan plan : unit.classes.values()) {
                    for (SitePlan site : plan.sites) {
                        members.add(site.generated);
                    }
                }
                ClassPlan host = unit.classes.get(unit.nestHostEntry);
                if (host == null) {
                    host = new ClassPlan(nest.bytes, Map.of(), List.of());
                    unit.classes.put(unit.nestHostEntry, host);
                }
                host.nestMembers = List.copyOf(members);
                planned.add(unit);
            }
            return new Plan(planned, List.copyOf(sites), compileOnly);
        }

        /**
         * Decides what happens to each site of one class.
         *
         * @return the class's plan, or {@code null} when it has no {@code metafactory} site
         * @throws RuntimeException if the class is malformed
         */
        private HostPlan evaluate(String entryName, byte[] bytes, List<Site> report) throws IOException {
            ClassModel host = PARSER.parse(bytes);
            if (!usesMetafactory(host)) {
                return null;
            }
            String hostName = host.thisClass().asInternalName();
            if (!entryName.equals(hostName + CLASS_SUFFIX)) {
                // No class loader defines it under this name.
                return null;
            }
            List<Found> found = new ArrayList<>();
            Map<String, Integer> sitesPerMethod = new HashMap<>();
            Map<String, Integer> methodOrder = new HashMap<>();
            for (MethodModel method : host.methods()) {
                Optional<CodeModel> code = method.code();
                if (code.isEmpty()) {
                    continue;
                }
                String methodKey = key(method);
                methodOrder.put(methodKey, methodOrder.size());
                int ordinal = 0;
                for (CodeElement element : code.get()) {
                    if (element instanceof InvokeDynamicInstruction indy) {
                        String bootstrap = bootstrap(indy);
                        if (METAFACTORY.equals(bootstrap)) {
                            found.add(new Found(methodKey, ordinal, indy));
                        } else if (ALT_METAFACTORY.equals(bootstrap)) {
                            report.add(new Site(hostName, methodKey, ordinal, null, Reason.ALT_METAFACTORY, null));
                        }
                        ordinal++;
                    }
                }
                sitesPerMethod.put(methodKey, ordinal);
            }
            HostPlan plan = new HostPlan();
            if (found.isEmpty()) {
                return plan;
            }
            Reason excluded = null;
            Nest nest = null;
            if (host.majorVersion() < NESTMATE_MAJOR) {
                excluded = Reason.CLASS_VERSION;
            } else {
                String nestHostName = host.findAttribute(Attributes.nestHost())
                        .map(attribute -> attribute.nestHost().asInternalName()).orElse(hostName);
                if (nestHostName.equals(hostName)) {
                    nest = new Nest(hostName, bytes, nestMembers(host));
                } else {
                    nest = nest(nestHostName);
                    if (nest == null || !nest.holds(hostName)) {
                        excluded = Reason.NEST;
                    }
                }
            }
            if (excluded != null) {
                for (Found site : found) {
                    report.add(new Site(hostName, site.method, site.ordinal, null, excluded, null));
                }
                return plan;
            }
            plan.nest = nest;
            String sourceFile = host.findAttribute(Attributes.sourceFile())
                    .map(attribute -> attribute.sourceFile().stringValue()).orElse(null);
            Host context = new Host(hostName, new Home(host.majorVersion(), host.minorVersion(),
                    ClassDesc.ofInternalName(nest.name), sourceFile), nest);
            for (Found site : found) {
                Object outcome = context.site(site.indy);
                if (outcome instanceof Kept kept) {
                    report.add(new Site(hostName, site.method, site.ordinal, null, kept.reason, kept.detail));
                    continue;
                }
                SitePlan planned = (SitePlan) outcome;
                plan.sites.add(planned);
                plan.sitesByMethod.computeIfAbsent(site.method,
                        method -> new SitePlan[sitesPerMethod.get(method)])[site.ordinal] = planned;
                report.add(new Site(hostName, site.method, site.ordinal, internalName(planned.generated), null, null));
            }
            report.sort((left, right) -> {
                int byMethod = Integer.compare(methodOrder.get(left.method), methodOrder.get(right.method));
                return byMethod != 0 ? byMethod : Integer.compare(left.ordinal, right.ordinal);
            });
            return plan;
        }

        /**
         * Reads a nest host that is not the class being planned.
         *
         * @return the nest, or {@code null} when the module holds no such class of version 55 or later
         */
        private Nest nest(String nestHostName) throws IOException {
            if (nests.containsKey(nestHostName)) {
                return nests.get(nestHostName);
            }
            Nest nest = null;
            Optional<ClassPathModel.Copy> copy = model.winner(nestHostName);
            if (copy.isPresent() && copy.get().root() == 0) {
                byte[] bytes = classes.read(nestHostName + CLASS_SUFFIX);
                if (bytes != null) {
                    try {
                        ClassModel parsed = PARSER.parse(bytes);
                        if (parsed.thisClass().asInternalName().equals(nestHostName)
                                && parsed.majorVersion() >= NESTMATE_MAJOR) {
                            nest = new Nest(nestHostName, bytes, nestMembers(parsed));
                        }
                    } catch (RuntimeException e) {
                        nest = null;
                    }
                }
            }
            nests.put(nestHostName, nest);
            return nest;
        }

        /**
         * One class being planned, with what its sites share.
         */
        private final class Host {

            private final String name;
            private final String packageName;
            private final Home home;
            private final Nest nest;
            private int next;

            private Host(String name, Home home, Nest nest) {
                this.name = name;
                this.packageName = packageOf(name);
                this.home = home;
                this.nest = nest;
            }

            /**
             * Decides one site.
             *
             * @return its {@link SitePlan}, or why it is {@link Kept}
             */
            private Object site(InvokeDynamicInstruction indy) {
                List<ConstantDesc> arguments = indy.bootstrapArgs();
                if (arguments.size() != 3 || !(arguments.get(0) instanceof MethodTypeDesc samType)
                        || !(arguments.get(1) instanceof DirectMethodHandleDesc implementation)
                        || !(arguments.get(2) instanceof MethodTypeDesc instantiatedType)) {
                    return new Kept(Reason.SHAPE, null);
                }
                MethodTypeDesc factoryType = indy.typeSymbol();
                String samName = indy.name().stringValue();
                ClassDesc functionalInterface = factoryType.returnType();
                if (!functionalInterface.isClassOrInterface() || !implementation.owner().isClassOrInterface()) {
                    return new Kept(Reason.SHAPE, null);
                }
                String owner = internalName(implementation.owner());
                Invocation invocation;
                boolean instance = true;
                boolean special = false;
                switch (implementation.kind()) {
                    case STATIC, INTERFACE_STATIC -> {
                        invocation = Invocation.STATIC;
                        instance = false;
                    }
                    case VIRTUAL -> invocation = Invocation.VIRTUAL;
                    case INTERFACE_VIRTUAL -> invocation = Invocation.INTERFACE;
                    case SPECIAL, INTERFACE_SPECIAL -> {
                        if (!owner.equals(name)) {
                            return new Kept(Reason.SUPER_CALL, null);
                        }
                        // As LambdaMetafactory does for a private method of the caller itself, and only for one:
                        // the member must turn out to be private below.
                        special = true;
                        invocation = implementation.isOwnerInterface() ? Invocation.INTERFACE : Invocation.VIRTUAL;
                    }
                    case CONSTRUCTOR -> {
                        invocation = Invocation.CONSTRUCTOR;
                        instance = false;
                    }
                    default -> {
                        return new Kept(Reason.SHAPE, null);
                    }
                }
                MethodTypeDesc implType = implementation.invocationType();
                MethodTypeDesc implDescriptor = MethodTypeDesc.ofDescriptor(implementation.lookupDescriptor());
                String implName = invocation == Invocation.CONSTRUCTOR ? CONSTRUCTOR : implementation.methodName();
                if (!shaped(factoryType, samName, samType, instantiatedType, implType, instance)) {
                    return new Kept(Reason.SHAPE, null);
                }

                int flags;
                boolean ownerIsInterface;
                if (JdkClasses.owns(packageOf(owner))) {
                    // The runtime asks the JDK first for this package, whatever the class path holds.
                    JdkClasses.JdkClass jdk = JdkClasses.find(owner);
                    if (jdk == null) {
                        return new Kept(Reason.UNRESOLVED_TYPE, owner);
                    }
                    if ((jdk.flags() & ClassFile.ACC_PUBLIC) == 0 || !jdk.exported()) {
                        return new Kept(Reason.OWNER_ACCESS, null);
                    }
                    Integer method = jdk.method(implName, implementation.lookupDescriptor());
                    if (method == null || (method & ClassFile.ACC_PUBLIC) == 0) {
                        return new Kept(Reason.OWNER_ACCESS, null);
                    }
                    if ((method & JdkClasses.JdkClass.CALLER_SENSITIVE) != 0) {
                        return new Kept(Reason.CALLER_SENSITIVE, null);
                    }
                    if ((method & ClassFile.ACC_NATIVE) != 0 && (method & ClassFile.ACC_VARARGS) != 0
                            && owner.startsWith("java/lang/invoke/")) {
                        // A signature-polymorphic method: its descriptor is the call site's, not the method's.
                        return new Kept(Reason.SHAPE, null);
                    }
                    flags = method;
                    ownerIsInterface = (jdk.flags() & ClassFile.ACC_INTERFACE) != 0;
                } else {
                    Optional<ClassPathModel.Copy> copy = model.winner(owner);
                    if (copy.isEmpty()) {
                        return new Kept(Reason.UNRESOLVED_TYPE, owner);
                    }
                    if (model.uncertain(owner)) {
                        return new Kept(Reason.SHADOWED_OR_UNCERTAIN, null);
                    }
                    ClassPathModel.Member member = copy.get().member(implName, implementation.lookupDescriptor());
                    if (member == null) {
                        return new Kept(Reason.OWNER_ACCESS, null);
                    }
                    flags = member.flags();
                    ownerIsInterface = copy.get().isInterface();
                    if ((flags & ClassFile.ACC_PRIVATE) != 0) {
                        if (!owner.equals(name) && !inNest(owner, copy.get())) {
                            return new Kept(Reason.OWNER_ACCESS, null);
                        }
                    } else if (!model.isAccessible(owner, implName, implementation.lookupDescriptor(), packageName)) {
                        return new Kept(Reason.OWNER_ACCESS, null);
                    }
                }
                if (special && (flags & ClassFile.ACC_PRIVATE) == 0) {
                    // LambdaMetafactory keeps an invokespecial of a member that is not private non-virtual, through
                    // the method handle; an invokevirtual would dispatch to an override in a subclass instead.
                    return new Kept(Reason.SUPER_CALL, null);
                }
                if (((flags & ClassFile.ACC_STATIC) != 0) != (invocation == Invocation.STATIC)
                        || ownerIsInterface != implementation.isOwnerInterface()
                        || ownerIsInterface && invocation == Invocation.CONSTRUCTOR) {
                    return new Kept(Reason.SHAPE, null);
                }
                if (kindOf(internalName(functionalInterface)) != INTERFACE) {
                    String missing = unresolved(functionalInterface);
                    return missing != null ? new Kept(Reason.UNRESOLVED_TYPE, missing) : new Kept(Reason.SHAPE, null);
                }
                String missing = unresolvedIn(factoryType, samType, instantiatedType, implType);
                if (missing != null) {
                    return new Kept(Reason.UNRESOLVED_TYPE, missing);
                }

                int number = next++;
                String generatedName = name + GENERATED_INFIX + number;
                if (model.known(generatedName)) {
                    return new Kept(Reason.NAME_TAKEN, null);
                }
                return new SitePlan(home, ClassDesc.ofInternalName(generatedName),
                        new Shape(factoryType, samName, samType, instantiatedType),
                        new Target(implementation.owner(), implementation.isOwnerInterface(), implName,
                                implDescriptor, implType, invocation));
            }

            /**
             * Whether the site has the shape {@code LambdaMetafactory} accepts and this step generates: the
             * implementation takes the captured values and then the interface method's arguments, the captured
             * values match exactly, and every argument and the result have a conversion.
             */
            private boolean shaped(MethodTypeDesc factoryType, String samName, MethodTypeDesc samType,
                                   MethodTypeDesc instantiatedType, MethodTypeDesc implType, boolean instance) {
                int captured = factoryType.parameterCount();
                int arity = samType.parameterCount();
                if (implType.parameterCount() != captured + arity || instantiatedType.parameterCount() != arity) {
                    return false;
                }
                if (samName.startsWith("<") || samName.equals(FACTORY_METHOD) && samType.equals(factoryType)) {
                    return false;
                }
                // A captured receiver may be a subclass of the owner; every other captured value is exact.
                int first = instance && captured > 0 ? 1 : 0;
                if (first == 1 && factoryType.parameterType(0).isPrimitive()) {
                    return false;
                }
                for (int i = first; i < captured; i++) {
                    if (!factoryType.parameterType(i).equals(implType.parameterType(i))) {
                        return false;
                    }
                }
                for (int i = 0; i < arity; i++) {
                    if (!Conversions.convertible(samType.parameterType(i), implType.parameterType(captured + i),
                            instantiatedType.parameterType(i))) {
                        return false;
                    }
                }
                ClassDesc expected = samType.returnType();
                if (isVoid(expected)) {
                    return true;
                }
                return Conversions.convertible(implType.returnType(), expected, expected);
            }

            /**
             * Whether a private member's owner, another class than the host, is in the host's nest: a class of the
             * module that names the nest host and that the nest host lists.
             */
            private boolean inNest(String owner, ClassPathModel.Copy copy) {
                if (copy.root() != 0 || !nest.holds(owner)) {
                    return false;
                }
                return owner.equals(nest.name) || nest.name.equals(copy.nestHost());
            }

            /**
             * The first type the generated class would name that does not resolve: a captured type, an argument,
             * a result or a cast of the call site, the interface method or the implementation.
             *
             * @return the type's internal name, or {@code null} when every type resolves
             */
            private String unresolvedIn(MethodTypeDesc... types) {
                for (MethodTypeDesc type : types) {
                    String missing = unresolved(type.returnType());
                    if (missing != null) {
                        return missing;
                    }
                    for (ClassDesc parameter : type.parameterList()) {
                        missing = unresolved(parameter);
                        if (missing != null) {
                            return missing;
                        }
                    }
                }
                return null;
            }
        }
    }

    /**
     * What one class's sites became while it is being planned.
     */
    private static final class HostPlan {
        private final List<SitePlan> sites = new ArrayList<>();
        private final Map<String, SitePlan[]> sitesByMethod = new HashMap<>();
        private Nest nest;
    }
}
