pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Cielo Smart"

include(":app")

// Leaf modules, depending on no feature at all.
include(":ui")
include(":core:money")

// Each feature splits into `core` (contract) and `common` (implementation).
include(":feature:shop:core")
include(":feature:shop:common")
include(":feature:checkout:common")
include(":feature:payment:core")
include(":feature:payment:common")
 