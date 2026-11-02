package fr.cartedueciel.tools

import fr.cartedueciel.catalog.SearchIndex
import fr.cartedueciel.catalog.SearchTarget
import fr.cartedueciel.catalog.SkyCatalogFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Tests « catalogue » du cahier des charges (section 9) sur les données réelles :
 * nombre d'étoiles conforme au filtre, 88 constellations présentes, aucune ligne pointant
 * vers une étoile absente.
 */
class CatalogBuilderTest {

    private val data = File("data")

    private fun build() = CatalogBuilder.build(
        File(data, "hyg/hyg_v41_subset.csv"),
        File(data, "d3-celestial/constellations.lines.json"),
        File(data, "d3-celestial/constellations.json"),
        6.5,
    )

    @Test
    fun `88 constellations, lignes resolues, filtre de magnitude`() {
        val r = build()
        val c = r.catalog
        assertEquals(88, c.constellations.size)
        assertEquals(88, c.constellations.map { it.abbreviation }.toSet().size)
        assertEquals(0, r.unmatchedVertices)
        assertTrue("écart max ${r.maxMatchSeparationDeg}", r.maxMatchSeparationDeg < 0.01)
        // Chaque constellation possède au moins un tracé et chaque tracé pointe vers des étoiles existantes.
        for (con in c.constellations) {
            assertTrue(con.abbreviation, con.polylineCount >= 1)
            for (p in con.firstPolyline until con.firstPolyline + con.polylineCount) {
                val line = c.polylines[p]
                assertEquals(con.index, line.constellationIndex)
                assertTrue(line.starIndices.size >= 2)
                line.starIndices.forEach { assertTrue(it in 0 until c.starCount) }
            }
        }
        // 8 920 étoiles de magnitude <= 6,5 dans HYG v4.1 (hors Soleil).
        assertEquals(8920, c.countUpToMagnitude(6.5))
        // Les étoiles sont triées par magnitude croissante.
        for (i in 1 until c.starCount) assertTrue(c.stars.magMilli[i - 1] <= c.stars.magMilli[i])
        // Sirius est la première étoile.
        assertEquals("Sirius", c.star(0).properName)
        assertEquals(32349, c.star(0).hip)
        assertEquals(2.6371, c.star(0).distancePc!!, 1e-3) // valeur du champ dist de HYG v4.1
        // Aucune étoile de magnitude <= 6,5 sans constellation.
        for (i in 0 until c.countUpToMagnitude(6.5)) assertTrue(c.stars.constellation[i] >= 0)
        println(r.report)
    }

    @Test
    fun `le fichier binaire se relit a l'identique et la recherche fonctionne`() {
        val r = build()
        val bytes = ByteArrayOutputStream().also { SkyCatalogFormat.write(r.catalog, it) }.toByteArray()
        assertTrue("taille ${bytes.size}", bytes.size < 600_000)
        val back = SkyCatalogFormat.read(ByteArrayInputStream(bytes))
        assertEquals(r.catalog.starCount, back.starCount)
        assertEquals(r.catalog.polylines.size, back.polylines.size)
        val index = SearchIndex(back, "fr")
        val orion = index.search("Orion").first()
        assertTrue(orion.target is SearchTarget.ConstellationTarget)
        val vega = index.search("vega").first()
        val star = back.star((vega.target as SearchTarget.StarTarget).index)
        assertEquals("Vega", star.properName)
        assertEquals("α Lyr", star.bayerDesignation)
        val betelgeuse = index.search("alpha orionis").first()
        assertEquals("Betelgeuse", back.star((betelgeuse.target as SearchTarget.StarTarget).index).properName)
        assertNotNull(back.constellationByAbbreviation("UMa"))
        assertEquals("Grande Ourse", back.constellationByAbbreviation("UMa")!!.nameFr)
        assertEquals("Ursa Maior", back.constellationByAbbreviation("UMa")!!.latinName) // orthographe latine de d3-celestial
    }
}
