package demo;

import dep.Transformer;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import opt.OptionalType;

public class Host implements Serializable {
    private final String prefix;

    public Host(String prefix) {
        this.prefix = prefix;
    }

    public static Supplier<String> captureFree() {
        return () -> "free";
    }

    public Function<String, String> capturing() {
        return s -> prefix + s;
    }

    public static Function<Integer, String> methodReference() {
        return Integer::toHexString;
    }

    public static Supplier<List<String>> constructorReference() {
        return ArrayList::new;
    }

    public static Runnable serializable() {
        return (Runnable & Serializable) () -> { };
    }

    private String secret() {
        return "secret:" + prefix;
    }

    public Supplier<String> privateImplementation() {
        return this::secret;
    }

    public static Transformer<String, Integer> dependencyInterface() {
        return String::length;
    }

    public static Supplier<String> compileOnly(OptionalType type) {
        return () -> type.name();
    }

    protected Supplier<String> inherited() {
        return () -> prefix;
    }
}
