#!/usr/bin/env python3
"""Extrait de la base HYG les lignes utiles à l'application.

Usage : make_hyg_subset.py <hygdata_v41.csv> <sortie.csv> [magnitude_max]

Règle d'inclusion (par défaut magnitude_max = 7.0) : toute étoile de magnitude
visuelle <= magnitude_max, ou possédant un nom propre, ou de nom de catalogue HR/HIP
référencé par les figures de constellations (toutes sont en pratique < 7). La ligne
« Sol » (id 0) est exclue : le Soleil est calculé par l'application.

Toutes les colonnes d'origine sont conservées, l'ordre des lignes aussi, afin que le
fichier reste un extrait fidèle (licence CC BY-SA 4.0, voir tools/data/hyg/README.md).
"""
import csv
import sys


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    mag_max = float(sys.argv[3]) if len(sys.argv) > 3 else 7.0
    kept = 0
    total = 0
    with open(src, newline="", encoding="utf-8") as fin, open(dst, "w", newline="", encoding="utf-8") as fout:
        reader = csv.DictReader(fin)
        writer = csv.DictWriter(fout, fieldnames=reader.fieldnames, lineterminator="\n")
        writer.writeheader()
        for row in reader:
            total += 1
            if row["id"] == "0":
                continue
            try:
                mag = float(row["mag"])
            except ValueError:
                continue
            if mag <= mag_max or row["proper"].strip():
                writer.writerow(row)
                kept += 1
    print(f"{kept} lignes conservées sur {total} (magnitude <= {mag_max} ou nom propre)")


if __name__ == "__main__":
    main()
