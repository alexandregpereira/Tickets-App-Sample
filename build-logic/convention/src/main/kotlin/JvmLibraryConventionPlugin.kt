import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure

/**
 * Módulo Kotlin puro, sem dependência do toolchain Android.
 *
 * Usado por `:core:money`, que só formata números: manter esse tipo de código fora do Android deixa
 * explícito que ele não depende de framework e o torna reaproveitável em qualquer alvo.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
}
