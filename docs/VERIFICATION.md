# Vérification

## Ce qui a été exécuté

Commande : `./gradlew -Pcdc.jvmOnly=true test` (JDK 21, Gradle 8.14.3, Kotlin 2.2.20).

| Module | Tests | Résultat | Contenu |
| --- | --- | --- | --- |
| `:geomag` | 4 | verts | 12 cas officiels WMM2025 (X, Y, Z, H, F, I, D, GV et variations séculaires), pôles géographiques, année décimale |
| `:astro` | 24 | verts | rotations et conventions ENU/équatorial ; quaternions (slerp, formule de `SensorManager`) ; projection/déprojection ; **positions types du §9** (vertical face au nord, à plat vers le ciel/le sol, paysage 90°/180°/270°, déclinaison, incliné à 45°) ; matrice J2000 → ENU comparée à `horizon()` d'Astronomy Engine sur 180 directions/dates/lieux (< 1″) ; Polaire au nord à la hauteur de la latitude ; Soleil plein sud à midi solaire ; lever/culmination/coucher du Soleil à Paris (± 30 min des valeurs attendues) ; Sirius (culmination 24,4°, hauteur −0,57° au lever), Canopus jamais levée, Polaire circumpolaire ; constellation de Sirius et de la Polaire ; année décimale |
| `:catalog` | 3 | verts | aller-retour du format binaire, comptage par magnitude, désignations, recherche (accents, casse, Bayer, planètes) |
| `:tools` | 2 | verts | sur données réelles : 88 constellations, tous les tracés pointent vers des étoiles présentes (écart max 0,0045°), 8 920 étoiles ≤ 6,5, tri par magnitude, Sirius en tête, relecture du binaire, recherche « Orion », « vega », « alpha orionis » |

Catalogue généré : `app/src/main/assets/catalog/sky_catalog.report.txt`.

## Vérification de type des modules Android (harnais hors dépôt)

Les artefacts AndroidX n'étant pas téléchargeables, les sources de `:sensors`, `:render`,
`:camera` et `:app` ont été compilées dans un projet JVM séparé avec :
- `android.jar` API 36 (copie du SDK hébergée sur GitHub, `Sable/android-platforms`) ;
- Compose Multiplatform 1.8.2 (runtime, ui, foundation, material3, icônes) et lifecycle
  multiplateforme 2.8.4 de JetBrains, publiés sur Maven Central, qui exposent les mêmes API que
  Jetpack Compose 1.8.2 / Material 3 ;
- des déclarations minimales (« stubs ») pour les API sans équivalent JVM : activity-compose,
  AppCompat, core, DataStore, CameraX, `AndroidView`, et des classes `R`/`BuildConfig` générées.

Résultat (2 novembre 2026) : les quatre modules Android (177 classes) compilent sans erreur dans
ce harnais, avec un seul avertissement (surcharge dépréciée `onStatusChanged`, désormais
supprimée explicitement). Deux erreurs réelles ont été trouvées et corrigées par ce moyen :
`setImmersive` masquait une méthode d'`Activity`, et un appel pleinement qualifié de l'extension
`rotate` de `DrawScope` ne se résolvait pas.

Cette vérification détecte les erreurs de type dans le code de l'application et son usage de
Compose/Material 3, mais **pas** les écarts éventuels entre les stubs et les vraies API AndroidX
(activity-compose, AppCompat, core, DataStore, CameraX, lifecycle), ni les problèmes
d'exécution (OpenGL, capteurs, caméra), ni les ressources Android (XML, manifeste).

## Reste à faire (recette §9)

- Compiler avec Android Studio (`./gradlew :app:assembleDebug`) et corriger les erreurs
  éventuelles.
- Tests automatisés supplémentaires : comparaison de 20 étoiles et des planètes avec Stellarium
  ou JPL Horizons (2′) — non réalisable hors-ligne ici ; la chaîne a été comparée à la fonction
  de référence de la bibliothèque, elle-même validée contre JPL Horizons par son auteur.
- Tests sur trois téléphones dont un sans gyroscope ; Polaire ; 10 étoiles brillantes ;
  stabilité après calibration ; émulateur sans capteurs ; mode avion ; refus de localisation ;
  revue « aucun pixel blanc » du mode nuit.
- Mesures : 60 i/s, démarrage < 2 s, taille < 15 Mo, consommation.
