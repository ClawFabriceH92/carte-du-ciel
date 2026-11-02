package fr.cartedueciel.astro.sky

import fr.cartedueciel.astro.math.Enu
import fr.cartedueciel.astro.math.Equatorial
import fr.cartedueciel.astro.math.Vec3
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.Vector
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.rotationEqjHor
import io.github.cosinekitty.astronomy.rotationEqjEqd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SkyFrameTest {

    private val paris = GeoPosition(48.8566, 2.3522, 35.0)

    /** Écart angulaire en secondes d'arc entre deux positions horizontales. */
    private fun separationArcsec(az1: Double, alt1: Double, az2: Double, alt2: Double): Double {
        val a = Enu.fromHorizontal(az1, alt1)
        val b = Enu.fromHorizontal(az2, alt2)
        return Math.toDegrees(a.angleTo(b)) * 3600.0
    }

    @Test
    fun `la conversion de matrice reproduit RotationMatrix_rotate`() {
        val t = Time(2026, 11, 2, 21, 0, 0.0)
        val r = rotationEqjHor(t, paris.toObserver())
        val m = SkyFrame.fromAstronomyEngine(r)
        val v = Vector(0.3, -0.5, 0.81, t)
        val expected = r.rotate(v)
        val actual = m * Vec3(v.x, v.y, v.z)
        assertEquals(expected.x, actual.x, 1e-12)
        assertEquals(expected.y, actual.y, 1e-12)
        assertEquals(expected.z, actual.z, 1e-12)
    }

    @Test
    fun `la chaine matricielle coincide avec horizon() d'Astronomy Engine`() {
        // 20 directions aléatoires reproductibles, 3 dates, 3 lieux. horizon() attend des coordonnées
        // équatoriales « de la date » : on précesse donc la direction J2000 avec rotationEqjEqd avant
        // l'appel. Écart attendu < 1" sans réfraction ; avec réfraction, les deux chemins appliquent
        // la même formule à la même hauteur vraie : écart également négligeable (< 1").
        val rnd = Random(42)
        val dates = listOf(Time(2026, 1, 15, 22, 0, 0.0), Time(2026, 7, 1, 1, 30, 0.0), Time(2029, 12, 31, 18, 0, 0.0))
        val places = listOf(paris, GeoPosition(-33.86, 151.21, 0.0), GeoPosition(64.13, -21.9, 0.0))
        var maxNoRefr = 0.0
        var maxRefr = 0.0
        for (t in dates) for (p in places) {
            val frame = SkyFrame(t, p)
            repeat(20) {
                val raHours = rnd.nextDouble() * 24.0
                val decDeg = rnd.nextDouble() * 160.0 - 80.0
                val eqj = Equatorial.fromRaDec(Math.toRadians(raHours * 15.0), Math.toRadians(decDeg))
                val ofDate = SkyFrame.fromAstronomyEngine(rotationEqjEqd(t)) * eqj
                val (raDateRad, decDateRad) = Equatorial.toRaDec(ofDate)
                val raDateHours = Math.toDegrees(raDateRad) / 15.0
                val decDateDeg = Math.toDegrees(decDateRad)
                val refNone = horizon(t, p.toObserver(), raDateHours, decDateDeg, Refraction.None)
                val mine = frame.horizontalOf(eqj, applyRefraction = false)
                maxNoRefr = maxOf(maxNoRefr, separationArcsec(refNone.azimuth, refNone.altitude, mine.azimuthDeg, mine.altitudeDeg))
                val refNormal = horizon(t, p.toObserver(), raDateHours, decDateDeg, Refraction.Normal)
                val mineR = frame.horizontalOf(eqj, applyRefraction = true)
                maxRefr = maxOf(maxRefr, separationArcsec(refNormal.azimuth, refNormal.altitude, mineR.azimuthDeg, mineR.altitudeDeg))
            }
        }
        assertTrue("écart max sans réfraction : $maxNoRefr\"", maxNoRefr < 1.0)
        assertTrue("écart max avec réfraction : $maxRefr\"", maxRefr < 1.0)
    }

    @Test
    fun `l'etoile Polaire est au nord a une hauteur voisine de la latitude`() {
        // Polaris (HIP 11767), J2000 : RA 2h31m49.09s, Dec +89°15'50.8" (valeurs du catalogue Hipparcos).
        val raRad = Math.toRadians((2.0 + 31.0 / 60.0 + 49.09 / 3600.0) * 15.0)
        val decRad = Math.toRadians(89.0 + 15.0 / 60.0 + 50.8 / 3600.0)
        val eqj = Equatorial.fromRaDec(raRad, decRad)
        for (hour in listOf(0, 6, 12, 18)) {
            val frame = SkyFrame(Time(2026, 11, 2, hour, 0, 0.0), paris)
            val h = frame.horizontalOf(eqj, applyRefraction = false)
            assertEquals(paris.latitudeDeg, h.altitudeDeg, 1.0)
            val dAz = minOf(h.azimuthDeg, 360.0 - h.azimuthDeg)
            assertTrue("azimut $dAz", dAz < 1.5)
        }
    }

    @Test
    fun `aller-retour ENU vers J2000 avec refraction`() {
        val frame = SkyFrame(Time(2026, 3, 21, 20, 0, 0.0), paris)
        val eqj = Equatorial.fromRaDec(1.0, 0.2)
        val enu = frame.toEnu(eqj, applyRefraction = true)
        val back = frame.toEqj(enu, removeRefraction = true)
        assertTrue(Math.toDegrees(eqj.angleTo(back)) * 3600.0 < 1.0)
    }

    @Test
    fun `le Soleil culmine au sud a Paris et le crepuscule suit sa hauteur`() {
        // Midi solaire approximatif à Paris le 2 novembre 2026 : vers 11h35 UTC (équation du temps ≈ +16 min, longitude 2,35° E ≈ 9 min).
        val noon = SkyFrame(Time(2026, 11, 2, 11, 35, 0.0), paris)
        val h = Enu.toHorizontal(noon.sunEnu)
        assertEquals(180.0, h.azimuthDeg, 2.0)
        assertTrue(h.altitudeDeg > 20.0 && h.altitudeDeg < 30.0) // 90 − 48.86 − 14.8 (déclinaison du Soleil) ≈ 26°
        assertEquals(Twilight.DAY, noon.twilight)
        val midnight = SkyFrame(Time(2026, 11, 2, 23, 30, 0.0), paris)
        assertEquals(Twilight.NIGHT, midnight.twilight)
        assertTrue(abs(midnight.sunAltitudeDeg + 55.0) < 10.0)
    }
}
