// Fichier racine volontairement vide de plugins : chaque module déclare les siens via le catalogue
// de versions (gradle/libs.versions.toml). Déclarer ici le plugin Kotlin (même avec `apply false`)
// placerait son artefact sur le classpath racine et ferait échouer la résolution de
// `org.jetbrains.kotlin.android` dans les modules Android (« plugin already on the classpath with
// an unknown version »). Les plugins Android ne sont résolus que si un module Android est inclus
// (voir settings.gradle.kts).
