// Carte du ciel — configuration des modules.
//
// Les modules purement JVM (:astro, :geomag, :catalog, :tools) sont toujours inclus.
// Les modules Android ne sont exclus que si la propriété Gradle `cdc.jvmOnly=true`
// est fournie : cela permet de compiler et tester la partie calcul sur une machine
// sans SDK Android ni accès à dl.google.com (serveur d'intégration, par exemple).
//
//   ./gradlew -Pcdc.jvmOnly=true test
pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "carte-du-ciel"

include(":astro", ":geomag", ":catalog", ":tools")

val jvmOnly = providers.gradleProperty("cdc.jvmOnly").map { it.toBoolean() }.getOrElse(false)
if (!jvmOnly) {
    include(":sensors", ":render", ":camera", ":app")
}
