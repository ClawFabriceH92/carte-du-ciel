package fr.cartedueciel.catalog

import fr.cartedueciel.astro.math.Equatorial
import fr.cartedueciel.astro.math.Vec3

/**
 * Catalogue d'étoiles et de constellations chargé en mémoire.
 *
 * Les étoiles sont triées par magnitude croissante : pour une magnitude limite donnée,
 * les étoiles visibles sont les `countUpToMagnitude(limite)` premières, ce qui permet de ne
 * dessiner qu'un préfixe des tableaux.
 */
class SkyCatalog(
    /** Magnitude maximale retenue à la construction (les étoiles nommées peuvent être plus faibles). */
    val buildMagnitudeLimit: Float,
    val strings: List<String>,
    val stars: StarTable,
    val constellations: List<Constellation>,
    val polylines: List<Polyline>,
) {
    val starCount: Int get() = stars.size

    /** Nombre d'étoiles de magnitude inférieure ou égale à `limit` (préfixe du tableau trié). */
    fun countUpToMagnitude(limit: Double): Int {
        val lim = (limit * 1000.0).toInt()
        // Recherche dichotomique du premier index de magnitude > limite.
        var lo = 0
        var hi = stars.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (stars.magMilli[mid] <= lim) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun constellationByAbbreviation(abbr: String): Constellation? = constellationsByAbbr[abbr]

    private val constellationsByAbbr: Map<String, Constellation> by lazy { constellations.associateBy { it.abbreviation } }

    /** Index des étoiles par numéro Hipparcos. */
    val starIndexByHip: Map<Int, Int> by lazy {
        val m = HashMap<Int, Int>(stars.size)
        for (i in 0 until stars.size) if (stars.hip[i] != 0) m[stars.hip[i]] = i
        m
    }

    /** Vue « objet » d'une étoile (allocation : à réserver aux fiches, recherches, libellés). */
    fun star(index: Int): Star = Star(this, index)

    override fun toString(): String = "SkyCatalog(${stars.size} étoiles, ${constellations.size} constellations, ${polylines.size} tracés)"
}

/** Tableaux parallèles décrivant les étoiles. */
class StarTable(
    val raRad: FloatArray,
    val decRad: FloatArray,
    /** Magnitude × 1000. */
    val magMilli: ShortArray,
    /** Indice de couleur B−V × 1000 ; [COLOR_UNKNOWN] si inconnu. */
    val colorMilli: ShortArray,
    val hip: IntArray,
    val hd: IntArray,
    /** Index de constellation ; [NO_CONSTELLATION] si absent. */
    val constellation: ByteArray,
    /** Lettre de Bayer (1..24) ou 0. */
    val bayer: ByteArray,
    /** Exposant de la lettre de Bayer (0 si aucun). */
    val bayerSuperscript: ByteArray,
    /** Numéro de Flamsteed ou 0. */
    val flamsteed: ShortArray,
    /** Identifiant de chaîne du nom propre ou 0. */
    val properNameId: ShortArray,
) {
    val size: Int get() = raRad.size

    /** Vecteurs unitaires J2000 (x, y, z entrelacés), calculés une fois. */
    val unitVectors: FloatArray by lazy {
        val out = FloatArray(size * 3)
        for (i in 0 until size) {
            val v = Equatorial.fromRaDec(raRad[i].toDouble(), decRad[i].toDouble())
            out[i * 3] = v.x.toFloat(); out[i * 3 + 1] = v.y.toFloat(); out[i * 3 + 2] = v.z.toFloat()
        }
        out
    }

    fun unitVector(i: Int): Vec3 = Equatorial.fromRaDec(raRad[i].toDouble(), decRad[i].toDouble())

    fun magnitude(i: Int): Double = magMilli[i] / 1000.0

    fun colorIndex(i: Int): Double = if (colorMilli[i] == COLOR_UNKNOWN) Double.NaN else colorMilli[i] / 1000.0

    companion object {
        const val COLOR_UNKNOWN: Short = Short.MAX_VALUE
        const val NO_CONSTELLATION: Byte = -1
    }
}

/** Constellation UAI. */
data class Constellation(
    val index: Int,
    val abbreviation: String,
    val latinName: String,
    val genitive: String,
    val nameFr: String,
    val nameEn: String,
    /** Position du libellé (J2000, radians). */
    val labelRaRad: Float,
    val labelDecRad: Float,
    /** Rang de proéminence (1 = le plus connu), d'après d3-celestial. */
    val rank: Int,
    val firstPolyline: Int,
    val polylineCount: Int,
) {
    val labelDirection: Vec3 get() = Equatorial.fromRaDec(labelRaRad.toDouble(), labelDecRad.toDouble())
}

/** Suite d'étoiles reliées par des segments (indices dans la table des étoiles). */
class Polyline(val constellationIndex: Int, val starIndices: IntArray)

/** Accès pratique à une étoile. */
class Star(val catalog: SkyCatalog, val index: Int) {
    private val t get() = catalog.stars
    val raRad: Double get() = t.raRad[index].toDouble()
    val decRad: Double get() = t.decRad[index].toDouble()
    val magnitude: Double get() = t.magnitude(index)
    val colorIndex: Double get() = t.colorIndex(index)
    val hip: Int get() = t.hip[index]
    val hd: Int get() = t.hd[index]
    val constellation: Constellation? get() = t.constellation[index].toInt().let { if (it < 0) null else catalog.constellations[it] }
    val properName: String? get() = t.properNameId[index].toInt().let { if (it <= 0) null else catalog.strings[it] }
    val unitVector: Vec3 get() = t.unitVector(index)

    /** Désignation de Bayer (« α Lyr », « κ¹ Boo ») ou null. */
    val bayerDesignation: String?
        get() {
            val b = t.bayer[index].toInt()
            if (b <= 0) return null
            val con = constellation?.abbreviation ?: return null
            return GreekLetters.symbol(b) + GreekLetters.superscript(t.bayerSuperscript[index].toInt()) + " " + con
        }

    /** Désignation de Flamsteed (« 58 Ori ») ou null. */
    val flamsteedDesignation: String?
        get() {
            val f = t.flamsteed[index].toInt()
            if (f <= 0) return null
            val con = constellation?.abbreviation ?: return null
            return "$f $con"
        }

    /** Meilleure désignation courte : nom propre, sinon Bayer, sinon Flamsteed, sinon HIP/HD. */
    val displayName: String
        get() = properName ?: bayerDesignation ?: flamsteedDesignation
            ?: if (hip != 0) "HIP $hip" else if (hd != 0) "HD $hd" else "Étoile #$index"

    /** Désignation de catalogue complémentaire au nom affiché. */
    val secondaryDesignation: String?
        get() {
            val parts = mutableListOf<String>()
            if (properName != null) bayerDesignation?.let { parts += it } ?: flamsteedDesignation?.let { parts += it }
            else if (bayerDesignation != null) flamsteedDesignation?.let { parts += it }
            if (hip != 0) parts += "HIP $hip"
            else if (hd != 0) parts += "HD $hd"
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}
