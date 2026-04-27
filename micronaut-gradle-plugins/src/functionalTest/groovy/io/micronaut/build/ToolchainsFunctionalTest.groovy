package io.micronaut.build

class ToolchainsFunctionalTest extends AbstractFunctionalTest {

    void "USE_GRADLE_TOOLCHAINS value '#valueLabel' configures useToolchains as #expected"() {
        given:
        withSample("test-micronaut-module")
        configureUseToolchainsEnvironment(value)

        file("subproject1/build.gradle") << """
            tasks.register("printUseToolchains") {
                doLast {
                    println "useToolchains=\${micronautBuild.useToolchains.get()}"
                }
            }
        """

        when:
        run ':subproject1:printUseToolchains'

        then:
        outputContains "useToolchains=${expected}"

        where:
        value   | expected | valueLabel
        null    | false    | "<unset>"
        ""      | true     | "<empty>"
        "true"  | true     | "true"
        "false" | false    | "false"
    }

    void "non-toolchain mode keeps source and target compatibility on javaVersion and leaves tests on current JDK"() {
        given:
        withSample("test-micronaut-module")
        configureUseToolchainsEnvironment(null)
        file("subproject1/build.gradle") << """
            micronautBuild {
                javaVersion = ${differentJdk}
                testJavaVersion = ${differentJdk}
            }

            ${printJavaConfigurationTask()}
        """

        when:
        run ':subproject1:printJavaConfiguration'

        then:
        outputContains "useToolchains=false"
        outputContains "sourceCompatibility=${differentJdk}"
        outputContains "targetCompatibility=${differentJdk}"
        outputContains "toolchainLanguageVersion=unset"
        outputContains "testJavaLauncher=${currentJdk}"
    }

    void "toolchain mode keeps compilation and test JVM versions independent"() {
        given:
        withSample("test-micronaut-module")
        configureUseToolchainsEnvironment("true")
        file("subproject1/build.gradle") << """
            micronautBuild {
                javaVersion = ${differentJdk}
                testJavaVersion = ${currentJdk}
            }

            ${printJavaConfigurationTask()}
        """

        when:
        run ':subproject1:printJavaConfiguration'

        then:
        outputContains "useToolchains=true"
        outputContains "toolchainLanguageVersion=${differentJdk}"
        outputContains "testJavaLauncher=${currentJdk}"
    }

    void "legacy compatibility overrides keep clearing toolchain language version"() {
        given:
        withSample("test-micronaut-module")
        configureUseToolchainsEnvironment("true")
        file("subproject1/build.gradle") << """
            micronautBuild {
                javaVersion = ${differentJdk}
                sourceCompatibility = "11"
            }

            ${printJavaConfigurationTask()}
        """

        when:
        run ':subproject1:printJavaConfiguration'

        then:
        outputContains """The "sourceCompatibility" and "targetCompatibility" properties are deprecated.
Please use "micronautBuild.javaVersion" instead.
You can do this directly in the project, or, better, in a convention plugin if it exists."""
        outputContains "sourceCompatibility=11"
        outputContains "targetCompatibility=11"
        outputContains "toolchainLanguageVersion=unset"
    }

    private void configureUseToolchainsEnvironment(String value) {
        if (value == null) {
            removedEnvironment << "USE_GRADLE_TOOLCHAINS"
        } else {
            environment["USE_GRADLE_TOOLCHAINS"] = value
        }
    }

    private String getCurrentJdk() {
        System.getProperty("CURRENT_JDK")
    }

    private String getDifferentJdk() {
        currentJdk == "17" ? "21" : "17"
    }

    private static String printJavaConfigurationTask() {
        """
            tasks.register("printJavaConfiguration") {
                doLast {
                    def javaExtension = project.extensions.getByName("java")
                    def testTask = project.tasks.named("test").get()
                    println "useToolchains=\${micronautBuild.useToolchains.get()}"
                    println "sourceCompatibility=\${javaExtension.sourceCompatibility}"
                    println "targetCompatibility=\${javaExtension.targetCompatibility}"
                    println "toolchainLanguageVersion=\${javaExtension.toolchain.languageVersion.orNull?.asInt() ?: 'unset'}"
                    println "testJavaLauncher=\${testTask.javaLauncher.orNull?.metadata?.languageVersion?.asInt() ?: 'unset'}"
                }
            }
        """
    }
}
