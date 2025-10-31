import org.gradle.api.provider.ProviderFactory

fun ProviderFactory.environmentVariableOrSystemProperty(env: String, sysProp: String) =
    environmentVariable(env)
        .orElse(systemProperty(sysProp))
        .orElse(gradleProperty(sysProp))
