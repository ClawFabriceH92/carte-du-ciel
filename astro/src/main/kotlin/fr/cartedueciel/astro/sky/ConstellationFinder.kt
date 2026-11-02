package fr.cartedueciel.astro.sky

import fr.cartedueciel.astro.math.Equatorial
import fr.cartedueciel.astro.math.Vec3
import io.github.cosinekitty.astronomy.constellation

/** Constellation UAI contenant une direction du ciel (limites officielles de 1930, via Astronomy Engine). */
object ConstellationFinder {
    /** Abréviation UAI à trois lettres (ex. « Ori ») de la constellation contenant la direction J2000. */
    fun abbreviationAt(eqjDir: Vec3): String {
        val (raRad, decRad) = Equatorial.toRaDec(eqjDir)
        val raHours = Math.toDegrees(raRad) / 15.0
        return constellation(raHours, Math.toDegrees(decRad)).symbol
    }
}
