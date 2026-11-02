package fr.cartedueciel.astro.sky

import fr.cartedueciel.astro.math.Equatorial
import io.github.cosinekitty.astronomy.Time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarSystemAndEventsTest {

    private val paris = GeoPosition(48.8566, 2.3522, 35.0)
    private val t0 = Time(2026, 11, 2, 18, 0, 0.0)

    @Test
    fun `etats des corps du Systeme solaire coherents`() {
        val states = SolarSystem.all(t0, paris)
        assertEquals(SolarBody.entries.size, states.size)
        val sun = states.first { it.body == SolarBody.SUN }
        assertTrue(sun.magnitude < -26.0)
        assertEquals(1.0, sun.distanceAu, 0.02)
        val moon = states.first { it.body == SolarBody.MOON }
        assertTrue(moon.distanceAu < 0.003)
        assertTrue(moon.phaseFraction in 0.0..1.0)
        assertNotNull(moon.moonPhaseName)
        val frame = SkyFrame(t0, paris)
        // À 18 h UTC début novembre, le Soleil est couché à Paris.
        assertTrue(frame.horizontalOf(sun.eqjDir).altitudeDeg < 0.0)
        // Les directions sont unitaires.
        states.forEach { assertEquals(1.0, it.eqjDir.length, 1e-9) }
    }

    @Test
    fun `phases lunaires nommees`() {
        assertEquals(MoonPhaseName.NEW, MoonPhaseName.fromPhaseAngle(5.0))
        assertEquals(MoonPhaseName.FIRST_QUARTER, MoonPhaseName.fromPhaseAngle(90.0))
        assertEquals(MoonPhaseName.FULL, MoonPhaseName.fromPhaseAngle(180.0))
        assertEquals(MoonPhaseName.LAST_QUARTER, MoonPhaseName.fromPhaseAngle(270.0))
        assertEquals(MoonPhaseName.WANING_CRESCENT, MoonPhaseName.fromPhaseAngle(300.0))
        assertEquals(MoonPhaseName.NEW, MoonPhaseName.fromPhaseAngle(359.0))
    }

    @Test
    fun `lever, culmination et coucher du Soleil a Paris`() {
        val ev = SkyEventCalculator.forBody(SolarBody.SUN, paris, Time(2026, 11, 2, 0, 0, 0.0))
        assertNotNull(ev.rise); assertNotNull(ev.set); assertNotNull(ev.culmination)
        val rise = ev.rise!!.toDateTime()
        val set = ev.set!!.toDateTime()
        // Début novembre à Paris : lever vers 6h40 UTC, coucher vers 16h30 UTC (±30 min).
        assertEquals(6.0 + 40.0 / 60.0, rise.hour + rise.minute / 60.0, 0.5)
        assertEquals(16.0 + 30.0 / 60.0, set.hour + set.minute / 60.0, 0.5)
        assertTrue(ev.culminationAltitudeDeg!! in 20.0..30.0)
        assertFalse(ev.circumpolar); assertFalse(ev.neverRises)
    }

    @Test
    fun `evenements d'etoiles fixes - Polaire circumpolaire, Canopus jamais levee, Sirius visible`() {
        val polaris = Equatorial.fromRaDec(Math.toRadians(37.95), Math.toRadians(89.264))
        val evP = SkyEventCalculator.forFixedPoint(polaris, paris, t0)
        assertTrue(evP.circumpolar); assertNull(evP.rise); assertNull(evP.set); assertNotNull(evP.culmination)

        // Canopus : RA 6h23m57s, Dec −52°41'44" (J2000).
        val canopus = Equatorial.fromRaDec(Math.toRadians(95.988), Math.toRadians(-52.696))
        val evC = SkyEventCalculator.forFixedPoint(canopus, paris, t0)
        assertTrue(evC.neverRises); assertNull(evC.rise); assertNull(evC.set)

        // Sirius : RA 6h45m08.9s, Dec −16°42'58" (J2000).
        val sirius = Equatorial.fromRaDec(Math.toRadians(101.287), Math.toRadians(-16.716))
        val evS = SkyEventCalculator.forFixedPoint(sirius, paris, t0)
        assertNotNull(evS.rise); assertNotNull(evS.set); assertNotNull(evS.culmination)
        // Hauteur de culmination = 90 − |φ − δ| = 90 − 65.57 ≈ 24.4°.
        assertEquals(24.4, evS.culminationAltitudeDeg!!, 0.3)
        // À la culmination calculée, l'étoile est bien plein sud et à cette hauteur (vérification croisée).
        val frame = SkyFrame(evS.culmination!!, paris)
        val h = frame.horizontalOf(sirius, applyRefraction = false)
        assertEquals(180.0, h.azimuthDeg, 0.5)
        assertEquals(24.4, h.altitudeDeg, 0.3)
        // Au lever calculé, la hauteur vraie vaut environ −0,57° (± 0,3°).
        val hr = SkyFrame(evS.rise!!, paris).horizontalOf(sirius, applyRefraction = false)
        assertEquals(SkyEventCalculator.STANDARD_ALTITUDE_DEG, hr.altitudeDeg, 0.3)
        // Tous les événements sont dans les 24 h.
        listOf(evS.rise!!, evS.set!!, evS.culmination!!).forEach { assertTrue(it.ut - t0.ut in 0.0..1.0) }
    }

    @Test
    fun `constellation contenant une direction`() {
        val sirius = Equatorial.fromRaDec(Math.toRadians(101.287), Math.toRadians(-16.716))
        assertEquals("CMa", ConstellationFinder.abbreviationAt(sirius))
        val polaris = Equatorial.fromRaDec(Math.toRadians(37.95), Math.toRadians(89.264))
        assertEquals("UMi", ConstellationFinder.abbreviationAt(polaris))
    }

    @Test
    fun `annee decimale WMM`() {
        // 1er janvier 2026 00:00 UTC = 1767225600000 ms.
        assertEquals(2026.0, SkyTime.decimalYear(1767225600000L), 1e-9)
        val t = SkyTime.fromEpochMillis(1767225600000L)
        val d = t.toDateTime()
        assertEquals(2026, d.year); assertEquals(1, d.month); assertEquals(1, d.day); assertEquals(0, d.hour)
        assertEquals(1767225600000L, SkyTime.toEpochMillis(t))
    }
}
