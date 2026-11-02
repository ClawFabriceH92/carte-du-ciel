# Astronomy Engine (Kotlin) — code embarqué

- Projet : Astronomy Engine, Don Cross — https://github.com/cosinekitty/astronomy
- Fichier repris tel quel : `source/kotlin/src/main/kotlin/io/github/cosinekitty/astronomy/astronomy.kt`
  (branche `master` du dépôt, version 2.1.19 d'après `source/kotlin/build.gradle.kts`,
  téléchargé le 2 novembre 2026).
- Licence : MIT (voir `LICENSE` dans ce dossier).

Pourquoi embarquer le source plutôt qu'une dépendance JitPack ? La bibliothèque est
publiée via JitPack ; embarquer le fichier unique (sans dépendance externe) rend le
build reproductible hors-ligne et évite une dépendance à un service tiers au moment
de la compilation. Le fichier n'est pas modifié ; toute mise à jour se fait en
remplaçant le fichier et en mettant à jour ce README.
