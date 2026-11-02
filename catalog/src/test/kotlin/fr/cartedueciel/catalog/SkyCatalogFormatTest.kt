package fr.cartedueciel.catalog

import fr.cartedueciel.astro.sky.SolarBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SkyCatalogFormatTest {

    private fun sample(): SkyCatalog {
        val strings = listOf("", "Véga", "Lyr", "Lyra", "Lyrae", "Lyre", "Lyra", "Ori", "Orion", "Orionis", "Orion", "Orion", "Bételgeuse")
        val stars = StarTable(
            raRad = floatArrayOf(4.8737f, 1.5497f, 4.9f),
            decRad = floatArrayOf(0.6769f, 0.1293f, 0.5f),
            magMilli = shortArrayOf(30, 420, 5900),
            colorMilli = shortArrayOf(0, 1850, StarTable.COLOR_UNKNOWN),
            hip = intArrayOf(91262, 27989, 0),
            hd = intArrayOf(172167, 39801, 12345),
            constellation = byteArrayOf(0, 1, StarTable.NO_CONSTELLATION),
            bayer = byteArrayOf(1, 1, 0),
            bayerSuperscript = byteArrayOf(0, 0, 0),
            flamsteed = shortArrayOf(3, 58, 0),
            properNameId = shortArrayOf(1, 12, 0),
            distancePc = floatArrayOf(7.68f, 152.67f, 100000f),
        )
        val cons = listOf(
            Constellation(0, "Lyr", "Lyra", "Lyrae", "Lyre", "Lyra", 4.9f, 0.6f, 1, 0, 1),
            Constellation(1, "Ori", "Orion", "Orionis", "Orion", "Orion", 1.4f, 0.1f, 1, 1, 1),
        )
        val lines = listOf(Polyline(0, intArrayOf(0, 2)), Polyline(1, intArrayOf(1, 2, 0)))
        return SkyCatalog(6.5f, strings, stars, cons, lines)
    }

    @Test
    fun `ecriture puis lecture restituent le catalogue`() {
        val src = sample()
        val bytes = ByteArrayOutputStream().also { SkyCatalogFormat.write(src, it) }.toByteArray()
        val back = SkyCatalogFormat.read(ByteArrayInputStream(bytes))
        assertEquals(3, back.starCount)
        assertEquals(6.5f, back.buildMagnitudeLimit)
        assertEquals(src.strings, back.strings)
        for (i in 0 until 3) {
            assertEquals(src.stars.raRad[i], back.stars.raRad[i], 0f)
            assertEquals(src.stars.decRad[i], back.stars.decRad[i], 0f)
            assertEquals(src.stars.magMilli[i], back.stars.magMilli[i])
            assertEquals(src.stars.colorMilli[i], back.stars.colorMilli[i])
            assertEquals(src.stars.hip[i], back.stars.hip[i])
            assertEquals(src.stars.hd[i], back.stars.hd[i])
            assertEquals(src.stars.constellation[i], back.stars.constellation[i])
            assertEquals(src.stars.bayer[i], back.stars.bayer[i])
            assertEquals(src.stars.flamsteed[i], back.stars.flamsteed[i])
            assertEquals(src.stars.properNameId[i], back.stars.properNameId[i])
            assertEquals(src.stars.distancePc[i], back.stars.distancePc[i], 0f)
        }
        assertEquals(7.68, back.star(0).distancePc!!, 1e-6)
        assertEquals(25.05, back.star(0).distanceLightYears!!, 0.01)
        assertNull(back.star(2).distancePc)
        assertEquals(2, back.constellations.size)
        assertEquals("Lyre", back.constellations[0].nameFr)
        assertEquals("Orionis", back.constellations[1].genitive)
        assertEquals(2, back.polylines.size)
        assertEquals(listOf(1, 2, 0), back.polylines[1].starIndices.toList())
        assertEquals(1, back.polylines[1].constellationIndex)
    }

    @Test
    fun `comptage par magnitude et designations`() {
        val c = sample()
        assertEquals(0, c.countUpToMagnitude(-1.0))
        assertEquals(1, c.countUpToMagnitude(0.03))
        assertEquals(2, c.countUpToMagnitude(0.42))
        assertEquals(2, c.countUpToMagnitude(5.0))
        assertEquals(3, c.countUpToMagnitude(6.5))
        val vega = c.star(0)
        assertEquals("Véga", vega.displayName)
        assertEquals("α Lyr", vega.bayerDesignation)
        assertEquals("3 Lyr", vega.flamsteedDesignation)
        assertEquals("α Lyr · HIP 91262", vega.secondaryDesignation)
        val anon = c.star(2)
        assertNull(anon.properName)
        assertNull(anon.bayerDesignation)
        assertEquals("HD 12345", anon.displayName)
        assertTrue(anon.colorIndex.isNaN())
        assertEquals(0, c.starIndexByHip[91262])
    }

    @Test
    fun `recherche insensible aux accents et a la casse`() {
        val index = SearchIndex(sample(), "fr")
        assertEquals(SearchTarget.StarTarget(0), index.search("vega").first().target)
        assertEquals(SearchTarget.StarTarget(0), index.search("VÉGA").first().target)
        assertEquals(SearchTarget.StarTarget(0), index.search("alpha lyrae").first().target)
        assertEquals(SearchTarget.StarTarget(1), index.search("alp ori").first().target)
        assertEquals(SearchTarget.ConstellationTarget(1), index.search("orion").first().target)
        assertEquals(SearchTarget.BodyTarget(SolarBody.JUPITER), index.search("jupi").first().target)
        assertEquals(SearchTarget.BodyTarget(SolarBody.MOON), index.search("lune").first().target)
        assertEquals(SearchTarget.BodyTarget(SolarBody.MOON), index.search("moon").first().target)
        assertTrue(index.search("zzzz").isEmpty())
        assertTrue(index.search("").isEmpty())
        // Préfixe de mot : « lyr » trouve la constellation et les étoiles de la Lyre.
        val lyr = index.search("lyr")
        assertTrue(lyr.any { it.target == SearchTarget.ConstellationTarget(0) })
        assertEquals("vega", SearchIndex.normalize("  Véga  "))
        assertEquals("kappa1 boo", SearchIndex.normalize("κ¹ Boo").replace("κ", "kappa"))
    }
}
