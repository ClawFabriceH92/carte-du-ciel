package fr.cartedueciel.geomag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparaison avec les valeurs de test officielles de WMM2025
 * (`WMM2025_TEST_VALUE_TABLE_FOR_REPORT.txt`, NOAA/NCEI et CIRES, distribué avec le
 * module Python officiel `wmm-calculator`, dépôt github.com/CIRES-Geomagnetism/wmm).
 *
 * Tolérances : 0,1 nT sur les composantes, 0,01° sur les angles, soit la précision
 * d'impression de la table (mêmes tolérances que la suite de tests officielle Python).
 */
class WorldMagneticModelTest {

    private data class Row(
        val date: Double, val heightKm: Double, val lat: Double, val lon: Double,
        val x: Double, val y: Double, val z: Double, val h: Double, val f: Double,
        val incl: Double, val decl: Double, val gv: Double,
        val xDot: Double, val yDot: Double, val zDot: Double, val hDot: Double, val fDot: Double,
        val iDot: Double, val dDot: Double,
    )

    private fun officialRows(): List<Row> {
        val text = javaClass.getResourceAsStream("/wmm/WMM2025_TEST_VALUE_TABLE_FOR_REPORT.txt")!!
            .bufferedReader().readText()
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val v = line.split(Regex("\\s+")).map { it.toDouble() }
                Row(
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11],
                    v[12], v[13], v[14], v[15], v[16], v[17], v[18],
                )
            }
    }

    @Test
    fun `l'en-tete du fichier de coefficients est celui de WMM2025`() {
        val model = WorldMagneticModel.wmm2025()
        assertEquals(2025.0, model.epoch, 0.0)
        assertEquals("WMM-2025", model.modelName)
        assertTrue(model.isValidAt(2026.84))
        assertFalse(model.isValidAt(2030.0))
    }

    @Test
    fun `les 12 cas de test officiels sont reproduits`() {
        val rows = officialRows()
        assertEquals(12, rows.size)
        val model = WorldMagneticModel.wmm2025()
        for (r in rows) {
            val e = model.compute(r.lat, r.lon, r.heightKm, r.date)
            val ctx = "lat=${r.lat} lon=${r.lon} h=${r.heightKm} t=${r.date}"
            assertEquals("X $ctx", r.x, e.x, 0.1)
            assertEquals("Y $ctx", r.y, e.y, 0.1)
            assertEquals("Z $ctx", r.z, e.z, 0.1)
            assertEquals("H $ctx", r.h, e.h, 0.1)
            assertEquals("F $ctx", r.f, e.f, 0.1)
            assertEquals("I $ctx", r.incl, e.inclination, 0.01)
            assertEquals("D $ctx", r.decl, e.declination, 0.01)
            if (!r.gv.isNaN()) assertEquals("GV $ctx", r.gv, e.gridVariation, 0.01)
            assertEquals("Xdot $ctx", r.xDot, e.xDot, 0.1)
            assertEquals("Ydot $ctx", r.yDot, e.yDot, 0.1)
            assertEquals("Zdot $ctx", r.zDot, e.zDot, 0.1)
            assertEquals("Hdot $ctx", r.hDot, e.hDot, 0.1)
            assertEquals("Fdot $ctx", r.fDot, e.fDot, 0.1)
            assertEquals("Idot $ctx", r.iDot, e.inclinationDot, 0.01)
            assertEquals("Ddot $ctx", r.dDot, e.declinationDot, 0.01)
        }
    }

    @Test
    fun `le calcul aux poles geographiques ne diverge pas`() {
        val model = WorldMagneticModel.wmm2025()
        val north = model.compute(90.0, 0.0, 0.0, 2026.0)
        val south = model.compute(-90.0, 0.0, 0.0, 2026.0)
        assertTrue(north.f.isFinite() && north.f > 40_000.0)
        assertTrue(south.f.isFinite() && south.f > 40_000.0)
        assertTrue(north.declination.isFinite())
        assertTrue(south.declination.isFinite())
    }

    @Test
    fun `annee decimale selon la convention du logiciel de reference`() {
        assertEquals(2025.0, WorldMagneticModel.decimalYear(2025, 1, false), 1e-12)
        assertEquals(2026.0 + 305.0 / 365.0, WorldMagneticModel.decimalYear(2026, 306, false), 1e-12)
        assertEquals(2028.0 + 60.0 / 366.0, WorldMagneticModel.decimalYear(2028, 61, true), 1e-12)
    }
}
