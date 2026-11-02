# Architecture

## Conventions de repères

- **EQJ** (équatorial J2000, Astronomy Engine) : x vers le point vernal, z vers le pôle nord
  céleste. Les étoiles du catalogue y sont stockées en (ascension droite, déclinaison) J2000 ;
  leurs vecteurs unitaires sont calculés une fois au chargement.
- **ENU** (repère ciel local de l'application) : x = est, y = nord géographique, z = zénith.
  Azimut compté depuis le nord vers l'est. Conversion depuis le repère « HOR » d'Astronomy Engine
  (x = nord, y = ouest, z = zénith) par `SkyFrame.HOR_TO_ENU`.
- **Repère appareil Android** : x à droite de l'écran, y vers le haut, z sortant de l'écran ; la
  caméra arrière regarde vers −z. `SensorManager.getRotationMatrixFromVector` donne la matrice
  appareil → monde magnétique (x = est, y = nord magnétique, z = zénith).
- **Repère caméra OpenGL** : x à droite de l'affichage, y vers le haut, regard vers −z. La matrice
  de vue (ENU → caméra) est `DeviceOrientation.viewMatrix(R, rotationÉcran, déclinaison, décalage)` :
  `(Rz(−δ) · R · M_affichage)ᵀ`, où `M_affichage` tient compte de `Display.getRotation()`.

## Chaîne de calcul

```
GPS (approx.) + horloge UTC ──► SkyFrame (1 Hz) : rotationEqjHor (précession, nutation, temps sidéral)
                                   │   ├─ étoiles : vecteurs J2000 × matrice → ENU, réfraction près de l'horizon
                                   │   ├─ figures, horizon, grille, libellés → SceneSnapshot
                                   │   └─ Soleil, Lune, planètes (1/min) : equator() topocentrique + illumination()
capteurs (≈50 Hz) ─ slerp ──────► DeviceOrientation.viewMatrix ─► ViewState (matrice 4×4, champ, anneaux)
                                   │   ├─ objet au réticule (produit scalaire avec la direction visée)
                                   │   └─ guidage (angle de la flèche, distance angulaire, vibration au centrage)
SceneSnapshot + ViewState ──────► SkyRenderer (fil OpenGL, 60 Hz) : sol, lignes, points, sprites, libellés
```

La déclinaison magnétique est calculée par `WorldMagneticModel.wmm2025()` (recalcul si le lieu
change ou une fois par jour) et appliquée comme rotation autour du zénith.

## Fils d'exécution

- Capteurs : `callbackFlow` collecté sur `Dispatchers.Default` ; chaque échantillon appelle
  `SkyEngine.onOrientation` (méthodes `@Synchronized`, coût de l'ordre de 10 k produits scalaires).
- Horloge : coroutine à 1 Hz appelant `SkyEngine.tick`.
- Rendu : fil `GLSurfaceView` ; il lit des instantanés immuables (`SceneSnapshot`, `ViewState`)
  publiés par référence `@Volatile`, sans verrou.
- Compose : observe `HudState` (≤ 10 Hz) pour le nom de l'objet visé, l'indicateur de boussole et
  la flèche de guidage ; la vue OpenGL n'est jamais recomposée au rythme des capteurs.

## Format du catalogue

Voir `SkyCatalogFormat` : en-tête `CDCS`, table de chaînes, étoiles triées par magnitude
(31 octets chacune : RA, Dec, magnitude, indice de couleur, HIP, HD, constellation, Bayer,
Flamsteed, nom propre, distance), constellations (noms latin/génitif/fr/en, position du libellé,
rang), tracés (indices d'étoiles). Le tri par magnitude permet de dessiner un préfixe des tableaux
pour une magnitude limite donnée.

## Mode nuit

Tous les shaders appliquent `applyNight` (seule la composante rouge subsiste), le fond est noir,
le thème Compose `NightColors` ne contient que des rouges et des noirs (y compris les surfaces
inverses des snackbars), et le fond caméra est désactivé. Les barres système sont masquées sur la
vue ciel. Limite connue : le clavier virtuel du système (recherche, coordonnées) n'est pas
contrôlable par l'application.
