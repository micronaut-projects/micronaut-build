package io.micronaut.build.quality

import groovy.json.JsonSlurper
import io.micronaut.build.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

class SonarLintFunctionalTest extends AbstractFunctionalTest {

    private static final String BUGGY = '''package demo;

public class Buggy {
    public int size(String value) {
        if (value == null) {
            return 0;
        }
        if (value == null) {
            return 1;
        }
        return value.length();
    }
}
'''

    private static final String CLEAN = '''package demo;

public class Clean {
    public int size(String value) {
        return value == null ? 0 : value.length();
    }
}
'''

    def setup() {
        settingsFile.text = "rootProject.name = 'sonarlint-demo'"
        buildFile.text = '''
            plugins {
                id("java")
                id("io.micronaut.build.internal.quality-checks")
            }
            repositories {
                mavenCentral()
            }
        '''
    }

    void "reports a bug and fails"() {
        given:
        file("src/main/java/demo/Buggy.java").text = BUGGY
        file("src/main/java/demo/Clean.java").text = CLEAN

        when:
        fails "sonarLint", "-PsonarLint.all=true"

        then:
        tasks { failed ':sonarLint' }
        outputContains "sonarlint: src/main/java/demo/Buggy.java:8:13: MAJOR BUG java:S2583 Change this condition so that it does not always evaluate to \"false\""
        outputContains "sonarlint: 1 gate-failing issue (BUG 1), "
        errorOutputContains "sonarlint: 1 gate-failing issue:\nsonarlint: src/main/java/demo/Buggy.java:8:13: MAJOR BUG java:S2583"

        when:
        def report = new JsonSlurper().parse(file("build/reports/sonarlint/sonarlint.json"))

        then:
        def bug = report.issues.find { it.rule == 'java:S2583' }
        bug.file == 'src/main/java/demo/Buggy.java'
        bug.line == 8
        bug.column == 13
        bug.endLine == 8
        bug.type == 'BUG'
        bug.severity == 'MAJOR'
        bug.gateFailing == true
        report.summary.gateFailing == 1
        report.summary.scope == 'all'
        file("build/reports/sonarlint/sonarlint.sarif").text.contains('"ruleId": "java:S2583"')
    }

    void "does not fail when failOnIssues is false"() {
        given:
        file("src/main/java/demo/Buggy.java").text = BUGGY
        buildFile << '''
            micronautBuild.sonarLint.failOnIssues = false
        '''

        when:
        run "sonarLint", "-PsonarLint.all=true"

        then:
        tasks { succeeded ':sonarLint' }
        outputContains "sonarlint: src/main/java/demo/Buggy.java:8:13: MAJOR BUG java:S2583"
    }

    void "a clean project passes and the task is up to date"() {
        given:
        file("src/main/java/demo/Clean.java").text = CLEAN

        when:
        run "sonarLint", "-PsonarLint.all=true"

        then:
        tasks { succeeded ':sonarLint' }
        outputContains "sonarlint: 0 gate-failing issues, 0 other issues in 1 analysed file"
        new JsonSlurper().parse(file("build/reports/sonarlint/sonarlint.json")).issues == []

        when:
        run "sonarLint", "-PsonarLint.all=true"

        then:
        result.task(':sonarLint').outcome == TaskOutcome.UP_TO_DATE
    }

    void "check runs sonarLint only when enabled"() {
        given:
        file("src/main/java/demo/Buggy.java").text = BUGGY

        when:
        run "check", "-PsonarLint.all=true", "-x", "checkstyleMain", "-x", "test"

        then:
        tasks { doesNotContain ':sonarLint' }

        when:
        buildFile << '''
            micronautBuild.sonarLint.enabled = true
        '''
        fails "check", "-PsonarLint.all=true", "-x", "checkstyleMain", "-x", "test"

        then:
        tasks { failed ':sonarLint' }
    }

    void "only reports the issues on lines changed since the base ref"() {
        given:
        file(".gitignore").text = "build/\n.gradle/\n"
        // The bug is in the base commit: an unchanged line
        file("src/main/java/demo/Buggy.java").text = BUGGY
        git "init", "-q", "-b", "main"
        git "add", "."
        git "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-q", "-m", "base"
        git "checkout", "-q", "-b", "feature"
        // A changed file with a bug on a changed line
        file("src/main/java/demo/Buggy.java").text = BUGGY.replace('''        return value.length();
''', '''        if (value == null) {
            return 2;
        }
        return value.length();
''')
        // A new, untracked file
        file("src/main/java/demo/Other.java").text = BUGGY.replace("Buggy", "Other")

        when:
        fails "sonarLint", "-PsonarLint.baseRef=main"

        then:
        outputContains "sonarlint: src/main/java/demo/Buggy.java:11:13: MAJOR BUG java:S2583"
        outputDoesNotContain "sonarlint: src/main/java/demo/Buggy.java:8:"
        outputContains "sonarlint: src/main/java/demo/Other.java:8:13: MAJOR BUG java:S2583"
        outputContains "sonarlint: 2 gate-failing issues (BUG 2)"
        outputContains " on changed lines in 2 analysed files"
        def report = new JsonSlurper().parse(file("build/reports/sonarlint/sonarlint.json"))
        report.summary.scope == 'changed-lines'
        report.issues.findAll { it.gateFailing }*.line == [11, 8]
    }

    void "skips the analysis when no source changed"() {
        given:
        file(".gitignore").text = "build/\n.gradle/\n"
        file("src/main/java/demo/Buggy.java").text = BUGGY
        git "init", "-q", "-b", "main"
        git "add", "."
        git "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-q", "-m", "base"

        when:
        run "sonarLint", "-PsonarLint.baseRef=main"

        then:
        tasks { succeeded ':sonarLint' }
        outputContains "sonarlint: 0 gate-failing issues, 0 other issues on changed lines in 0 analysed files"

        when: "a change is committed, the task is not up to date"
        file("src/main/java/demo/Buggy.java").text = BUGGY.replace('''        return value.length();
''', '''        if (value == null) {
            return 2;
        }
        return value.length();
''')
        git "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-q", "-a", "-m", "change"
        fails "sonarLint", "-PsonarLint.baseRef=main~1"

        then:
        outputContains "sonarlint: src/main/java/demo/Buggy.java:11:13: MAJOR BUG java:S2583"
        outputContains "sonarlint: 1 gate-failing issue (BUG 1), 0 other issues on changed lines in 1 analysed file"
    }

    void "explains how to fix a missing base ref"() {
        given:
        file("src/main/java/demo/Clean.java").text = CLEAN
        git "init", "-q", "-b", "main"
        git "add", "."
        git "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-q", "-m", "base"

        when:
        fails "sonarLint", "-PsonarLint.baseRef=origin/does-not-exist"

        then:
        errorOutputContains "cannot find the merge base of 'origin/does-not-exist' and HEAD"
        errorOutputContains "-PsonarLint.all=true"
    }

    private void git(String... args) {
        def process = new ProcessBuilder(["git", *args]).directory(testDirectory.toFile()).redirectErrorStream(true).start()
        def out = process.inputStream.text
        assert process.waitFor() == 0: "git ${args.join(' ')} failed: $out"
    }
}
