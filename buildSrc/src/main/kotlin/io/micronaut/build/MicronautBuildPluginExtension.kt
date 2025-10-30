package io.micronaut.build

import org.gradle.api.Action
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration

abstract class MicronautBuildPluginExtension(val gradlePlugins: GradlePluginDevelopmentExtension) {

    abstract val versionsFullyQualifiedClassName: Property<String>

    abstract val versionsMap: MapProperty<String, String>

    fun definePlugin(name: String, pluginClassName: String) = gradlePlugins.plugins.register(name) {
        id = "io.micronaut.build.internal.$name"
        implementationClass = pluginClassName
    }

    fun definePlugin(name: String, pluginClassName: String, config: Action<PluginDeclaration>) = gradlePlugins.plugins.register(name) {
        id = "io.micronaut.build.internal.$name"
        implementationClass = pluginClassName
        config.execute(this)
    }
}
