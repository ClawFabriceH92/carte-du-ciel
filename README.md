# Carte du ciel — application Android de carte du ciel en réalité augmentée

Application Android native (Kotlin, Jetpack Compose, OpenGL ES 2.0) qui affiche en temps réel
les étoiles, constellations, Soleil, Lune et planètes situés dans l'axe de la caméra arrière du
téléphone. Le ciel est **calculé** (position + heure + orientation), pas détecté : l'application
fonctionne de jour, en intérieur, par temps couvert, et 100 % hors-ligne (aucune permission
Internet).

Ce dépôt met en œuvre le cahier des charges du 1er novembre 2026 (lots 1 à 3, périmètre v1).

## État du projet (2 novembre 2026)

| Partie | État | Vérification |
| --- | --- | --- |
| `:geomag` — World Magnetic Model 2025 (port Kotlin, coefficients embarqués) | terminé | 4 tests, dont les **12 cas de test officiels NOAA** (écarts < 0,1 nT et < 0,01°) |
| `:astro` — Astronomy Engine 2.1.19 embarqué (MIT), géométrie, orientation appareil → vue, repère local J2000 → ENU, réfraction, éphémérides, lever/coucher | terminé | 24 tests (positions types du §9, chaîne matricielle comparée à `horizon()` d'Astronomy Engine à < 1″, Polaire, Soleil, Sirius…) |
| `:catalog` — format binaire, modèle, recherche | terminé | 3 tests |
| `:tools` — construction du catalogue depuis HYG v4.1 et d3-celestial | terminé | 2 tests sur données réelles : 88 constellations, 893 sommets de figures tous appariés (écart max 0,0045°), 8 920 étoiles ≤ 6,5 |
| `app/src/main/assets/catalog/sky_catalog.bin` | généré (294 Ko, 9 028 étoiles) | relu à l'identique par les tests |
| `:sensors`, `:render`, `:camera`, `:app` (Android) | code écrit, **non compilé avec le SDK Android** dans l'environnement de développement (voir ci-dessous) | compilation de type réussie (0 erreur, 177 classes) dans un harnais JVM : Compose Multiplatform 1.8.2 + `android.jar` API 36 + stubs AndroidX ; voir `docs/VERIFICATION.md` pour les limites |

**Important** : l'environnement dans lequel ce code a été produit n'avait pas accès à
`dl.google.com` (plugin Android Gradle, bibliothèques AndroidX, SDK Android). Les modules
Android n'ont donc **pas** pu être compilés ni exécutés sur un appareil. La première ouverture
dans Android Studio peut révéler des erreurs de compilation à corriger ; les tests sur appareils
du §9 du cahier des charges restent à faire. Les modules de calcul (`:astro`, `:geomag`,
`:catalog`, `:tools`) ont, eux, été compilés et testés (33 tests verts).

## Construire

Prérequis : Android Studio (ou SDK Android avec `compileSdk 36`), JDK 17 ou plus récent.
Le wrapper Gradle (8.14.3) est fourni.

```bash
# Application de débogage (génère d'abord le catalogue si tools/data a changé)
./gradlew :app:assembleDebug

# Tests unitaires des modules de calcul, sans SDK Android
./gradlew -Pcdc.jvmOnly=true test

# Régénérer le catalogue binaire
./gradlew :tools:generateCatalog
```

La propriété `-Pcdc.jvmOnly=true` exclut les modules Android de la configuration Gradle, ce qui
permet de compiler et tester la partie calcul sur une machine sans SDK Android.

## Architecture (modules)

| Module | Rôle | Dépendances |
| --- | --- | --- |
| `:astro` | Astronomy Engine (source MIT embarqué), `Vec3`/`Mat3`/`Quaternion`, `DeviceOrientation` (matrice Android → matrice de vue, rotation d'écran, déclinaison), `SkyCamera` (projection), `SkyFrame` (J2000 → ENU, réfraction, crépuscule), `SolarSystem`, `SkyEventCalculator`, `ConstellationFinder` | — |
| `:geomag` | `WorldMagneticModel` (WMM2025) | — |
| `:catalog` | `SkyCatalog`, `SkyCatalogFormat` (lecture/écriture), `SearchIndex`, `GreekLetters` | `:astro` |
| `:tools` | `CatalogBuilder` + tâche Gradle `generateCatalog` ; données sources dans `tools/data` | `:catalog` |
| `:sensors` | `OrientationProvider` (rotation vector, repli géomagnétique, lissage slerp, qualité boussole), `DisplayRotation`, `CoarseLocationProvider` (sans services Google) | `:astro` |
| `:render` | `SkyRenderer` OpenGL ES 2.0 (étoiles, lignes, sol, Soleil/Lune/planètes, libellés sans chevauchement, mode nuit, fond transparent), `SkyGLSurfaceView` | `:astro` |
| `:camera` | `CameraPreview` (CameraX) et `CameraFov` (estimation du champ) | — |
| `:app` | `SkyEngine` (scène par seconde, vue par échantillon, réticule, guidage, fiches), `SkyViewModel`, écrans Compose, réglages DataStore, thème jour/nuit, FR/EN | tous |

Repères utilisés partout : ciel en **ENU** (x = est, y = nord géographique, z = zénith) ;
caméra OpenGL (x à droite, y en haut, regard vers −z). Voir `docs/ARCHITECTURE.md`.

## Données embarquées et licences

| Donnée | Source | Licence | Détails |
| --- | --- | --- | --- |
| Étoiles | HYG v4.1, David Nash — extrait `tools/data/hyg/hyg_v41_subset.csv` | CC BY-SA 4.0 | `tools/data/hyg/README.md` (provenance, empreintes SHA-256, pourquoi v4.1 et non v4.2) |
| Figures et noms des constellations | d3-celestial, Olaf Frohn — `tools/data/d3-celestial/` | BSD 3-Clause | `tools/data/d3-celestial/README.md` |
| Calculs astronomiques | Astronomy Engine 2.1.19, Don Cross — `astro/third_party/astronomy-engine/` | MIT | source Kotlin repris tel quel |
| Déclinaison magnétique | WMM2025, NOAA/NCEI et BGS — `geomag/src/main/resources/wmm/WMM2025.COF` | domaine public | coefficients identiques dans le module Python officiel de la NOAA et dans pygeomag ; table de test officielle dans `geomag/src/test/resources/wmm/` |

Le fichier `sky_catalog.bin` est dérivé de HYG et donc diffusé sous CC BY-SA 4.0 ; la portée
exacte de la clause de partage à l'identique sur le reste de l'application reste à faire valider
par un juriste (cahier des charges §6). La licence du code de l'application n'est pas encore
choisie.

## Documentation

- `docs/ARCHITECTURE.md` — conventions de repères, chaîne de calcul, cadences, fils d'exécution.
- `docs/CONFORMITE.md` — correspondance avec le cahier des charges, décisions prises, écarts.
- `docs/VERIFICATION.md` — ce qui a été vérifié, comment, et ce qui reste à faire (recette §9).
