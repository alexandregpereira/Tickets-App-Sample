import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Biblioteca Android que expõe UI em Compose.
 *
 * Aplica o convention plugin base, liga o compilador do Compose e já traz o BOM + as dependências de
 * Compose que toda tela do app usa, para os módulos de feature não repetirem a mesma lista.
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("cielosmart.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures {
                compose = true
            }
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            val bom = platform(libs.findLibrary("androidx-compose-bom").get())
            add("implementation", bom)
            add("androidTestImplementation", bom)
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
