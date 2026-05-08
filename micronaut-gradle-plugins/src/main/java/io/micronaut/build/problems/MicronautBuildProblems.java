package io.micronaut.build.problems;

import org.gradle.api.Action;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;

import java.util.regex.Pattern;

public final class MicronautBuildProblems {
    public static final String DOCUMENTATION_URL = "https://github.com/micronaut-projects/micronaut-build#gradle-problems-api-diagnostics";

    private static final int MAX_DIAGNOSTIC_LENGTH = 1_000;
    private static final String SENSITIVE_KEYS = "authorization|password|passwd|secret|token|api[-_ ]?key|access[-_ ]?key|username|user";
    private static final Pattern SENSITIVE_QUOTED_KEY_VALUE = Pattern.compile("(?i)([\"']?(?:" + SENSITIVE_KEYS + ")[\"']?\\s*[:=]\\s*)([\"'])(?:bearer|basic)?\\s*.*?\\2");
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile("(?i)([\"']?(?:" + SENSITIVE_KEYS + ")[\"']?\\s*[:=]\\s*)(?:bearer|basic)?\\s*[^\"'\\s,;)}\\]]+");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern BASIC_TOKEN = Pattern.compile("(?i)basic\\s+[a-z0-9._~+/=-]+");

    public static final ProblemGroup MICRONAUT_BUILD = ProblemGroup.create("micronaut-build", "Micronaut Build");
    public static final ProblemGroup VALIDATION = ProblemGroup.create("validation", "Micronaut Build validation", MICRONAUT_BUILD);

    public static final ProblemId ENFORCED_PLATFORM_NOT_SUPPORTED = validationProblem("enforced-platform-not-supported", "Enforced platform is not supported");
    public static final ProblemId MICRONAUT_VERSION_MISMATCH = validationProblem("micronaut-version-mismatch", "Micronaut version mismatch");
    public static final ProblemId UNSUPPORTED_TEST_FRAMEWORK = validationProblem("unsupported-test-framework", "Unsupported test framework");
    public static final ProblemId INVALID_POM_COORDINATES = validationProblem("invalid-pom-coordinates", "Invalid POM coordinates");
    public static final ProblemId POM_VERIFICATION_FAILED = validationProblem("pom-verification-failed", "POM verification failed");
    public static final ProblemId ASCIIDOC_OUTPUT_VALIDATION_FAILED = validationProblem("asciidoc-output-validation-failed", "Asciidoc output validation failed");
    public static final ProblemId MAVEN_CENTRAL_DEPLOYMENT_FAILED = validationProblem("maven-central-deployment-failed", "Maven Central deployment failed");

    private MicronautBuildProblems() {
    }

    public static RuntimeException throwing(Problems problems, Throwable exception, ProblemId id, Action<? super ProblemSpec> action) {
        return problems.getReporter().throwing(exception, id, spec -> {
            spec.severity(Severity.ERROR)
                .documentedAt(DOCUMENTATION_URL);
            action.execute(spec);
        });
    }

    public static String sanitizeDiagnosticText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ");
        sanitized = SENSITIVE_QUOTED_KEY_VALUE.matcher(sanitized).replaceAll("$1$2<redacted>$2");
        sanitized = SENSITIVE_KEY_VALUE.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer <redacted>");
        sanitized = BASIC_TOKEN.matcher(sanitized).replaceAll("Basic <redacted>");
        if (sanitized.length() > MAX_DIAGNOSTIC_LENGTH) {
            return sanitized.substring(0, MAX_DIAGNOSTIC_LENGTH) + "... (truncated)";
        }
        return sanitized;
    }

    private static ProblemId validationProblem(String name, String displayName) {
        return ProblemId.create(name, displayName, VALIDATION);
    }
}
