# Extrait de la base HYG v4.1

- Source : base HYG (Hipparcos, Yale Bright Star Catalog, Gliese), David Nash,
  dépôt d'archive GitHub `astronexus/HYG-Database`, fichier `hyg/CURRENT/hygdata_v41.csv`
  (119 626 étoiles), téléchargé le 2 novembre 2026.
  Empreinte SHA-256 du fichier complet téléchargé :
  voir `SHA256SUMS` dans ce dossier.
- Licence : Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0),
  d'après le `README.md` du dépôt. Le présent extrait et le fichier binaire dérivé
  (`app/src/main/assets/catalog/sky_catalog.bin`) sont donc eux aussi diffusés sous
  CC BY-SA 4.0, avec attribution à David Nash / astronexus.com.
- Pourquoi v4.1 et non v4.2 : la version 4.2 mentionnée dans le cahier des charges n'est
  publiée que sur Codeberg (`codeberg.org/astronexus/hyg`), inaccessible depuis
  l'environnement de développement utilisé. Le dépôt GitHub, gelé à la v4.1, est le
  dernier état public disponible par cette voie. Pour passer en v4.2 : remplacer le
  fichier source, relancer `tools/scripts/make_hyg_subset.py`, puis `./gradlew :tools:generateCatalog`.
- Contenu de l'extrait : lignes de magnitude visuelle <= 7,0 ou portant un nom propre,
  toutes colonnes conservées (voir `make_hyg_subset.py`).

Description des champs : voir le README du dépôt HYG (id, hip, hd, hr, gl, bf, proper,
ra [heures], dec [degrés], dist [pc], pmra, pmdec, rv, mag, absmag, spect, ci, x, y, z,
vx, vy, vz, rarad, decrad, pmrarad, pmdecrad, bayer, flam, con, comp, comp_primary, base,
lum, var, var_min, var_max).
