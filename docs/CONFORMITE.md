# Correspondance avec le cahier des charges et décisions

## §2 Périmètre fonctionnel (v1)

| Fonction | Mise en œuvre |
| --- | --- |
| Vue ciel « fenêtre » | `SkyScreen` + `SkyGLSurfaceView` ; vue mise à jour à chaque échantillon du capteur |
| Étoiles à l'œil nu | magnitude limite 6,5 par défaut, réglable 1,0–6,5 (curseur et préréglages ville 3,5 / campagne 5,5 / ciel noir 6,5) ; taille selon magnitude, couleur selon B−V (`StarColor`, approximation documentée) |
| 88 constellations | lignes d3-celestial appariées aux étoiles HYG ; noms français (ou anglais) ; interrupteurs séparés « figures » et « noms » |
| Soleil, Lune, planètes | `SolarSystem` (Astronomy Engine), phase de la Lune calculée et dessinée avec le limbe éclairé orienté vers le Soleil |
| Horizon et repères | ligne d'horizon, graduations, N/E/S/O, sol assombri, grille azimutale ou équatoriale |
| Réticule et fiche objet | objet le plus proche du centre (rayon ≈ 4,5 % du champ, borné 1–4°) ; sinon constellation contenant la direction ; fiche en panneau glissant |
| Recherche guidée | `SearchIndex` (accents ignorés, Bayer, constellations, planètes) ; flèche autour du réticule, anneau sur l'objet, vibration au centrage (< 1,5°) |
| Mode nuit | thème rouge/noir sur tous les écrans, shaders rouges, caméra coupée |
| Mode manuel | glisser (rotation) et pincer (champ 15–110°) ; imposé si aucun capteur |
| Fond caméra | CameraX `PreviewView`, champ estimé depuis Camera2, facteur de correction manuel |

## §3 Parcours et écrans

Premier lancement en trois étapes (principe, localisation avec choix d'une ville en repli,
calibration avec indicateur en direct). Vue ciel plein écran avec boutons flottants (recherche,
couches, nuit, caméra, manuel, paramètres). Fiche objet, recherche, couches, paramètres (lieu,
décalage boussole, unités, langue, lissage, correction du champ caméra, à propos) : voir `app/ui`.

## §4 Orientation

- `TYPE_ROTATION_VECTOR`, repli `TYPE_GEOMAGNETIC_ROTATION_VECTOR` (lissage renforcé), sinon
  mode manuel (`OrientationProvider`).
- Précision du cap : cinquième valeur du capteur (API 18+), sinon `onAccuracyChanged` ;
  seuils vert ≤ 5°, orange ≤ 15°, rouge au-delà (`OrientationSample.compassQuality`).
- La matrice de rotation est utilisée directement (équivalent du `remapCoordinateSystem(AXIS_X, AXIS_Z)`
  de la documentation Android) avec prise en compte de `Display.getRotation()` ; tests sur les
  positions types dans `DeviceOrientationTest`.
- Déclinaison : WMM2025 embarqué et calculé dans l'application ; `GeomagneticField` d'Android
  n'est pas utilisé (il pourra servir de contrôle dans un test instrumenté).
- `SENSOR_DELAY_GAME`, pas de permission `HIGH_SAMPLING_RATE_SENSORS`. Lissage par slerp,
  coefficient réglable.
- Calibration : indicateur permanent, message « décrire un 8 », décalage manuel par étoile connue
  (`SkyViewModel.calibrateOn`).

## §5 Calculs

Astronomy Engine Kotlin 2.1.19 embarqué en source (JitPack n'était pas accessible, et cela rend
le build reproductible hors-ligne). Matrice J2000 → horizontal recalculée chaque seconde, corps
du Système solaire chaque minute, réfraction appliquée près de l'horizon. Mouvement propre des
étoiles ignoré, altitude 0 m sauf si fournie par le GPS (hypothèses v1 du cahier des charges).

## §6 Données

HYG **v4.1** (dépôt GitHub) au lieu de v4.2 (Codeberg inaccessible) : voir
`tools/data/hyg/README.md`. 464 noms propres dans cette version (le cahier des charges cite
652 noms UAI en 2026 : l'écart sera comblé en passant à HYG v4.2). d3-celestial : fichiers
`constellations.lines.json` et `constellations.json` ; les fichiers des limites et des objets de
Messier sont déjà copiés pour la v2.

## §7 Architecture

Modules conformes au tableau du cahier des charges ; OpenGL ES retenu dès le lot 1. Android 8.0
(API 26) minimal, `compileSdk`/`targetSdk` 36.

## §8 Non fonctionnel

- Permissions : `ACCESS_COARSE_LOCATION`, `CAMERA` (demandée à l'activation du fond caméra),
  `VIBRATE` (ajout par rapport au cahier des charges, pour le retour haptique ; permission
  « normale », accordée automatiquement). Pas d'`INTERNET`.
- Capteurs et horloge arrêtés hors premier plan (`repeatOnLifecycle(STARTED)`), caméra liée au
  cycle de vie.
- Accessibilité : nom de l'objet visé et sa direction en région « live » TalkBack, descriptions
  des boutons, vibration au centrage. Taille de police système respectée par Compose.
- Langues : français par défaut (`values/`), anglais (`values-en/`), choix dans l'application
  (AppCompat `setApplicationLocales`).

## Écarts et points ouverts

1. **Compilation Android non effectuée** dans l'environnement de développement (hôte
   `dl.google.com` bloqué) : à faire en priorité dans Android Studio.
2. Versions AGP / AndroidX / CameraX du catalogue `gradle/libs.versions.toml` non vérifiées en
   ligne (versions stables de 2025 connues) ; à aligner sur les versions disponibles.
3. Champ de vision caméra : estimation à valider sur plusieurs modèles (§4), facteur manuel prévu.
4. Taille installée < 15 Mo et 60 i/s : à mesurer (§8).
5. Noms propres d'étoiles : passage à HYG v4.2 pour les 652 noms UAI.
6. Validation juridique CC BY-SA 4.0 (§6).
