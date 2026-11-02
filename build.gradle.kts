// Fichier racine volontairement minimal : chaque module déclare ses propres plugins
// via le catalogue de versions (gradle/libs.versions.toml). Les plugins Android ne
// sont résolus que lorsqu'un module Android est inclus (voir settings.gradle.kts).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
