package fr.cartedueciel.geomag

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Éléments du champ géomagnétique en un point et à une date.
 *
 * Toutes les composantes sont exprimées dans le repère géodétique local :
 * X vers le nord géographique, Y vers l'est, Z vers le bas.
 *
 * @property x composante nord (nT)
 * @property y composante est (nT)
 * @property z composante verticale, positive vers le bas (nT)
 * @property h intensité horizontale (nT)
 * @property f intensité totale (nT)
 * @property inclination inclinaison (degrés, positive vers le bas)
 * @property declination déclinaison (degrés, positive vers l'est) : angle entre
 *   le nord magnétique et le nord géographique. Cap vrai = cap magnétique + déclinaison.
 * @property gridVariation variation de grille (degrés), définie au-delà de ±55° de latitude
 *   comme dans le logiciel de référence ; égale à la déclinaison ailleurs.
 * @property xDot, yDot, zDot, hDot, fDot variation séculaire (nT/an)
 * @property inclinationDot, declinationDot variation séculaire (degrés/an)
 */
data class GeomagneticElements(
    val x: Double,
    val y: Double,
    val z: Double,
    val h: Double,
    val f: Double,
    val inclination: Double,
    val declination: Double,
    val gridVariation: Double,
    val xDot: Double,
    val yDot: Double,
    val zDot: Double,
    val hDot: Double,
    val fDot: Double,
    val inclinationDot: Double,
    val declinationDot: Double,
)

/**
 * Implémentation Kotlin du World Magnetic Model (WMM).
 *
 * Algorithme : développement en harmoniques sphériques de degré et ordre 12 avec
 * fonctions de Legendre associées semi-normalisées de Schmidt, conversion des
 * coordonnées géodétiques WGS84 en coordonnées sphériques géocentriques, puis rotation
 * du vecteur champ dans le repère géodétique. La structure suit le code de référence
 * en C publié par la NOAA/NCEI (domaine public) : GeodeticToSpherical,
 * ComputeSphericalHarmonicVariables, AssociatedLegendreFunction (PcupLow), Summation,
 * SummationSpecial, SecVarSummation, RotateMagneticVector, CalculateGeoMagneticElements,
 * CalculateSecularVariationElements.
 *
 * Les coefficients WMM2025 (fichier `WMM2025.COF`, époque 2025.0, valable du
 * 1er janvier 2025 au 31 décembre 2029) sont embarqués dans les ressources.
 *
 * Validation : voir `WorldMagneticModelTest`, qui compare aux valeurs de test officielles
 * publiées avec le modèle (fichier `WMM2025_TEST_VALUE_TABLE_FOR_REPORT.txt`).
 */
class WorldMagneticModel private constructor(
    /** Époque du modèle (année décimale), 2025.0 pour WMM2025. */
    val epoch: Double,
    /** Nom du modèle tel qu'indiqué dans l'en-tête du fichier de coefficients. */
    val modelName: String,
    private val nMax: Int,
    private val mainG: DoubleArray,
    private val mainH: DoubleArray,
    private val svG: DoubleArray,
    private val svH: DoubleArray,
) {

    /** Début de validité officielle (année décimale, incluse). */
    val validFrom: Double get() = epoch

    /** Fin de validité officielle (année décimale, exclue) : 5 ans après l'époque. */
    val validUntil: Double get() = epoch + 5.0

    /** Vrai si la date est dans la période de validité du modèle. */
    fun isValidAt(decimalYear: Double): Boolean = decimalYear >= validFrom && decimalYear < validUntil

    /**
     * Calcule les éléments du champ.
     *
     * @param latitudeDeg latitude géodétique WGS84 en degrés (nord positif)
     * @param longitudeDeg longitude en degrés (est positif)
     * @param heightKm hauteur au-dessus de l'ellipsoïde WGS84 en kilomètres
     * @param decimalYear date en année décimale (ex. 2026.5)
     */
    fun compute(latitudeDeg: Double, longitudeDeg: Double, heightKm: Double, decimalYear: Double): GeomagneticElements {
        require(latitudeDeg in -90.0..90.0) { "latitude hors de [-90, 90] : $latitudeDeg" }
        require(longitudeDeg in -360.0..360.0) { "longitude hors de [-360, 360] : $longitudeDeg" }

        // 1. Coefficients à la date demandée (extrapolation linéaire depuis l'époque).
        val dt = decimalYear - epoch
        val g = DoubleArray(mainG.size) { mainG[it] + dt * svG[it] }
        val h = DoubleArray(mainH.size) { mainH[it] + dt * svH[it] }

        // 2. Coordonnées géodétiques -> sphériques géocentriques.
        val phi = Math.toRadians(latitudeDeg)
        val lambda = Math.toRadians(longitudeDeg)
        val cosLat = cos(phi)
        val sinLat = sin(phi)
        val rc = WGS84_A / sqrt(1.0 - WGS84_EPS_SQ * sinLat * sinLat)
        val xp = (rc + heightKm) * cosLat
        val zp = (rc * (1.0 - WGS84_EPS_SQ) + heightKm) * sinLat
        val r = sqrt(xp * xp + zp * zp)
        val phiGeocentric = asin(zp / r)

        // 3. Variables sphériques : puissances du rayon relatif, cos(m*lambda), sin(m*lambda).
        val relativeRadiusPower = DoubleArray(nMax + 1)
        relativeRadiusPower[0] = (REFERENCE_RADIUS_KM / r) * (REFERENCE_RADIUS_KM / r)
        for (n in 1..nMax) relativeRadiusPower[n] = relativeRadiusPower[n - 1] * (REFERENCE_RADIUS_KM / r)
        val cosMLambda = DoubleArray(nMax + 1)
        val sinMLambda = DoubleArray(nMax + 1)
        cosMLambda[0] = 1.0
        sinMLambda[0] = 0.0
        val cosLambda = cos(lambda)
        val sinLambda = sin(lambda)
        cosMLambda[1] = cosLambda
        sinMLambda[1] = sinLambda
        for (m in 2..nMax) {
            cosMLambda[m] = cosMLambda[m - 1] * cosLambda - sinMLambda[m - 1] * sinLambda
            sinMLambda[m] = cosMLambda[m - 1] * sinLambda + sinMLambda[m - 1] * cosLambda
        }

        // 4. Fonctions de Legendre associées (semi-normalisées de Schmidt) et dérivées en latitude.
        val sinPhiG = sin(phiGeocentric)
        val cosPhiG = cos(phiGeocentric)
        val (pcup, dpcup) = legendre(sinPhiG, cosPhiG)

        // 5. Sommation du champ principal et de la variation séculaire (repère sphérique).
        val main = summation(g, h, relativeRadiusPower, cosMLambda, sinMLambda, pcup, dpcup, sinPhiG, cosPhiG, cosLambda, sinLambda)
        val sv = summation(svG, svH, relativeRadiusPower, cosMLambda, sinMLambda, pcup, dpcup, sinPhiG, cosPhiG, cosLambda, sinLambda)

        // 6. Rotation du repère géocentrique vers le repère géodétique.
        val psi = phiGeocentric - phi
        val x = main[0] * cos(psi) - main[2] * sin(psi)
        val y = main[1]
        val z = main[0] * sin(psi) + main[2] * cos(psi)
        val xDot = sv[0] * cos(psi) - sv[2] * sin(psi)
        val yDot = sv[1]
        val zDot = sv[0] * sin(psi) + sv[2] * cos(psi)

        // 7. Éléments dérivés.
        val hh = sqrt(x * x + y * y)
        val ff = sqrt(hh * hh + z * z)
        val decl = Math.toDegrees(atan2(y, x))
        val incl = Math.toDegrees(atan2(z, hh))
        val gv = normalizeDegrees(
            when {
                latitudeDeg > 55.0 -> decl - longitudeDeg
                latitudeDeg < -55.0 -> decl + longitudeDeg
                else -> decl
            },
        )
        val hDot = (x * xDot + y * yDot) / hh
        val fDot = (x * xDot + y * yDot + z * zDot) / ff
        val declDot = Math.toDegrees((x * yDot - y * xDot) / (hh * hh))
        val inclDot = Math.toDegrees((hh * zDot - z * hDot) / (ff * ff))

        return GeomagneticElements(
            x = x, y = y, z = z, h = hh, f = ff,
            inclination = incl, declination = decl, gridVariation = gv,
            xDot = xDot, yDot = yDot, zDot = zDot, hDot = hDot, fDot = fDot,
            inclinationDot = inclDot, declinationDot = declDot,
        )
    }

    /** Ramène un angle en degrés dans ]−180, 180]. */
    private fun normalizeDegrees(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    /** Déclinaison magnétique (degrés, est positif) : raccourci pour l'usage courant. */
    fun declination(latitudeDeg: Double, longitudeDeg: Double, heightKm: Double, decimalYear: Double): Double =
        compute(latitudeDeg, longitudeDeg, heightKm, decimalYear).declination

    /**
     * Fonctions de Legendre associées semi-normalisées de Schmidt P(n,m)(sin φ') et leurs
     * dérivées par rapport à la latitude géocentrique φ'. Indexation : n(n+1)/2 + m.
     */
    private fun legendre(x: Double, z: Double): Pair<DoubleArray, DoubleArray> {
        val size = (nMax + 1) * (nMax + 2) / 2
        val p = DoubleArray(size)
        val dp = DoubleArray(size)
        p[0] = 1.0
        dp[0] = 0.0
        for (n in 1..nMax) {
            for (m in 0..n) {
                val index = n * (n + 1) / 2 + m
                when {
                    n == m -> {
                        val index1 = (n - 1) * n / 2 + m - 1
                        p[index] = z * p[index1]
                        dp[index] = z * dp[index1] + x * p[index1]
                    }
                    n == 1 && m == 0 -> {
                        val index1 = (n - 1) * n / 2 + m
                        p[index] = x * p[index1]
                        dp[index] = x * dp[index1] - z * p[index1]
                    }
                    else -> {
                        val index1 = (n - 2) * (n - 1) / 2 + m
                        val index2 = (n - 1) * n / 2 + m
                        if (m > n - 2) {
                            p[index] = x * p[index2]
                            dp[index] = x * dp[index2] - z * p[index2]
                        } else {
                            val k = (((n - 1) * (n - 1)) - (m * m)).toDouble() / ((2 * n - 1) * (2 * n - 3)).toDouble()
                            p[index] = x * p[index2] - k * p[index1]
                            dp[index] = x * dp[index2] - z * p[index2] - k * dp[index1]
                        }
                    }
                }
            }
        }
        // Normalisation de Schmidt et passage de la dérivée en colatitude à la dérivée en latitude.
        val norm = DoubleArray(size)
        norm[0] = 1.0
        for (n in 1..nMax) {
            val index = n * (n + 1) / 2
            val index1 = (n - 1) * n / 2
            norm[index] = norm[index1] * (2 * n - 1).toDouble() / n.toDouble()
            for (m in 1..n) {
                val idx = n * (n + 1) / 2 + m
                val idx1 = n * (n + 1) / 2 + m - 1
                norm[idx] = norm[idx1] * sqrt(((n - m + 1) * (if (m == 1) 2 else 1)).toDouble() / (n + m).toDouble())
            }
        }
        for (n in 1..nMax) {
            for (m in 0..n) {
                val index = n * (n + 1) / 2 + m
                p[index] *= norm[index]
                dp[index] = -dp[index] * norm[index]
            }
        }
        return p to dp
    }

    /** Sommation des harmoniques : retourne (Bx, By, Bz) dans le repère sphérique géocentrique. */
    private fun summation(
        g: DoubleArray,
        h: DoubleArray,
        relativeRadiusPower: DoubleArray,
        cosMLambda: DoubleArray,
        sinMLambda: DoubleArray,
        p: DoubleArray,
        dp: DoubleArray,
        sinPhiG: Double,
        cosPhiG: Double,
        cosLambda: Double,
        sinLambda: Double,
    ): DoubleArray {
        var bx = 0.0
        var by = 0.0
        var bz = 0.0
        for (n in 1..nMax) {
            for (m in 0..n) {
                val index = n * (n + 1) / 2 + m
                val gcHs = g[index] * cosMLambda[m] + h[index] * sinMLambda[m]
                bz -= relativeRadiusPower[n] * gcHs * (n + 1) * p[index]
                by += relativeRadiusPower[n] * (g[index] * sinMLambda[m] - h[index] * cosMLambda[m]) * m * p[index]
                bx -= relativeRadiusPower[n] * gcHs * dp[index]
            }
        }
        if (abs(cosPhiG) > 1.0e-10) {
            by /= cosPhiG
        } else {
            // Cas particulier des pôles géographiques (division par cos φ' impossible).
            by = summationSpecial(g, h, relativeRadiusPower, sinPhiG, cosLambda, sinLambda)
        }
        return doubleArrayOf(bx, by, bz)
    }

    private fun summationSpecial(
        g: DoubleArray,
        h: DoubleArray,
        relativeRadiusPower: DoubleArray,
        sinPhiG: Double,
        cosLambda: Double,
        sinLambda: Double,
    ): Double {
        val pcupS = DoubleArray(nMax + 1)
        pcupS[0] = 1.0
        var schmidtQuasiNorm1 = 1.0
        var by = 0.0
        for (n in 1..nMax) {
            val index = n * (n + 1) / 2 + 1
            val schmidtQuasiNorm2 = schmidtQuasiNorm1 * (2 * n - 1).toDouble() / n.toDouble()
            val schmidtQuasiNorm3 = schmidtQuasiNorm2 * sqrt((n * 2).toDouble() / (n + 1).toDouble())
            schmidtQuasiNorm1 = schmidtQuasiNorm2
            if (n == 1) {
                pcupS[n] = pcupS[n - 1]
            } else {
                val k = (((n - 1) * (n - 1)) - 1).toDouble() / ((2 * n - 1) * (2 * n - 3)).toDouble()
                pcupS[n] = sinPhiG * pcupS[n - 1] - k * pcupS[n - 2]
            }
            by += relativeRadiusPower[n] * (g[index] * sinLambda - h[index] * cosLambda) * pcupS[n] * schmidtQuasiNorm3
        }
        return by
    }

    companion object {
        /** Demi-grand axe WGS84 (km). */
        const val WGS84_A = 6378.137
        /** Aplatissement WGS84. */
        const val WGS84_F = 1.0 / 298.257223563
        /** Carré de la première excentricité WGS84. */
        val WGS84_EPS_SQ: Double = WGS84_F * (2.0 - WGS84_F)
        /** Rayon de référence géomagnétique (km). */
        const val REFERENCE_RADIUS_KM = 6371.2
        /** Degré maximal du développement. */
        const val MAX_DEGREE = 12

        private val cached: WorldMagneticModel by lazy { loadResource("/wmm/WMM2025.COF") }

        /** Modèle WMM2025 embarqué. */
        fun wmm2025(): WorldMagneticModel = cached

        /** Charge un fichier de coefficients au format `.COF` de la NOAA depuis les ressources. */
        fun loadResource(path: String): WorldMagneticModel {
            val stream = WorldMagneticModel::class.java.getResourceAsStream(path)
                ?: throw IllegalStateException("Ressource introuvable : $path")
            return stream.use { parse(it) }
        }

        /**
         * Analyse un fichier `.COF` : une ligne d'en-tête (époque, nom, date), puis une ligne
         * par couple (n, m) avec g, h, ġ, ḣ, terminée par des lignes de « 9 ».
         */
        fun parse(stream: InputStream): WorldMagneticModel {
            val lines = stream.bufferedReader().readLines()
            require(lines.isNotEmpty()) { "Fichier de coefficients vide" }
            val header = lines.first().trim().split(Regex("\\s+"))
            require(header.size >= 2) { "En-tête de coefficients invalide : '${lines.first()}'" }
            val epoch = header[0].toDouble()
            val name = header[1]
            val nMax = MAX_DEGREE
            val size = (nMax + 1) * (nMax + 2) / 2
            val g = DoubleArray(size)
            val h = DoubleArray(size)
            val gDot = DoubleArray(size)
            val hDot = DoubleArray(size)
            var count = 0
            for (line in lines.drop(1)) {
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("9999")) continue
                val parts = t.split(Regex("\\s+"))
                require(parts.size >= 6) { "Ligne de coefficients invalide : '$line'" }
                val n = parts[0].toInt()
                val m = parts[1].toInt()
                if (n > nMax) continue
                val index = n * (n + 1) / 2 + m
                g[index] = parts[2].toDouble()
                h[index] = parts[3].toDouble()
                gDot[index] = parts[4].toDouble()
                hDot[index] = parts[5].toDouble()
                count++
            }
            require(count == size - 1) { "Nombre de coefficients inattendu : $count (attendu ${size - 1})" }
            return WorldMagneticModel(epoch, name, nMax, g, h, gDot, hDot)
        }

        /**
         * Année décimale au sens du logiciel de référence : année + (jour de l'année − 1) / nombre
         * de jours de l'année (365 ou 366).
         */
        fun decimalYear(year: Int, dayOfYear: Int, isLeapYear: Boolean): Double =
            year + (dayOfYear - 1).toDouble() / (if (isLeapYear) 366.0 else 365.0)
    }
}
