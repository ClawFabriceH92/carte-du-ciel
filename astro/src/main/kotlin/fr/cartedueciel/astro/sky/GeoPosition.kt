package fr.cartedueciel.astro.sky

import io.github.cosinekitty.astronomy.Observer

/**
 * Position de l'observateur.
 *
 * @property latitudeDeg latitude géodétique WGS84 (degrés, nord positif)
 * @property longitudeDeg longitude (degrés, est positif)
 * @property altitudeM altitude au-dessus du niveau de la mer (mètres) ; 0 si inconnue, comme le
 *   prévoit le cahier des charges (hypothèse simplificatrice de la v1).
 */
data class GeoPosition(val latitudeDeg: Double, val longitudeDeg: Double, val altitudeM: Double = 0.0) {
    init {
        require(latitudeDeg in -90.0..90.0) { "latitude invalide : $latitudeDeg" }
        require(longitudeDeg in -180.0..180.0) { "longitude invalide : $longitudeDeg" }
    }

    fun toObserver(): Observer = Observer(latitudeDeg, longitudeDeg, altitudeM)

    companion object {
        /** Lieu par défaut quand la localisation est refusée : Paris (Observatoire, approx.). */
        val PARIS = GeoPosition(48.8566, 2.3522, 35.0)
    }
}
