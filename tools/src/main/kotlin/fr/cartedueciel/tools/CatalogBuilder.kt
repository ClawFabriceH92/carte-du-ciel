package fr.cartedueciel.tools

import fr.cartedueciel.catalog.Constellation
import fr.cartedueciel.catalog.GreekLetters
import fr.cartedueciel.catalog.Polyline
import fr.cartedueciel.catalog.SkyCatalog
import fr.cartedueciel.catalog.SkyCatalogFormat
import fr.cartedueciel.catalog.StarTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Construit le catalogue binaire à partir de l'extrait HYG et des fichiers d3-celestial.
 *
 * Arguments : `<hyg.csv> <constellations.lines.json> <constellations.json> <sortie.bin> [magnitudeLimite]`
 */
fun main(args: Array<String>) {
    require(args.size >= 4) { "Usage : CatalogBuilder <hyg.csv> <lines.json> <names.json> <sortie.bin> [magnitudeLimite]" }
    val magLimit = args.getOrNull(4)?.toDouble() ?: 6.5
    val result = CatalogBuilder.build(File(args[0]), File(args[1]), File(args[2]), magLimit)
    val out = File(args[3])
    out.parentFile?.mkdirs()
    out.outputStream().use { SkyCatalogFormat.write(result.catalog, it) }
    File(out.parentFile, out.nameWithoutExtension + ".report.txt").writeText(result.report)
    println(result.report)
    println("Écrit : ${out.absolutePath} (${out.length()} octets)")
}

/** Ligne HYG utile. */
data class HygStar(
    val id: Int,
    val hip: Int,
    val hd: Int,
    val proper: String,
    val raHours: Double,
    val decDeg: Double,
    val mag: Double,
    val ci: Double,
    val distPc: Double,
    val bayer: String,
    val flam: Int,
    val con: String,
)

class BuildResult(val catalog: SkyCatalog, val report: String, val unmatchedVertices: Int, val maxMatchSeparationDeg: Double)

object CatalogBuilder {

    /** Tolérance d'appariement d'un sommet de figure à une étoile HYG (degrés). */
    const val MATCH_TOLERANCE_DEG = 0.05

    fun build(hygCsv: File, linesJson: File, namesJson: File, magnitudeLimit: Double): BuildResult {
        val hyg = readHyg(hygCsv)
        val names = readConstellationNames(namesJson)
        val lines = readLines(linesJson)

        // 1. Appariement des sommets de figures aux étoiles HYG (par le voisin le plus proche).
        val finder = NearestStarFinder(hyg)
        val lineStarIds = HashSet<Int>()
        var unmatched = 0
        var maxSep = 0.0
        val matchedLines = lines.map { line ->
            val ids = line.vertices.map { (raDeg, decDeg) ->
                val (star, sep) = finder.nearest(raDeg, decDeg)
                if (sep > MATCH_TOLERANCE_DEG) unmatched++
                if (sep > maxSep) maxSep = sep
                lineStarIds += star.id
                star.id
            }
            line.abbr to ids
        }

        // 2. Sélection : magnitude <= limite, ou nom propre, ou étoile d'une figure.
        val selected = hyg.filter { it.mag <= magnitudeLimit || it.proper.isNotEmpty() || it.id in lineStarIds }
            .sortedWith(compareBy<HygStar> { it.mag }.thenBy { it.id })
        val indexById = HashMap<Int, Int>(selected.size)
        selected.forEachIndexed { i, s -> indexById[s.id] = i }

        // 3. Constellations (ordre : abréviation), avec leurs tracés.
        val abbrs = names.keys.sorted()
        require(abbrs.size == 88) { "88 constellations attendues, ${abbrs.size} trouvées" }
        val conIndex = abbrs.withIndex().associate { it.value to it.index }
        val strings = ArrayList<String>().apply { add("") }
        val stringIds = HashMap<String, Int>().apply { put("", 0) }
        fun sid(s: String): Int = stringIds.getOrPut(s) { strings.add(s); strings.size - 1 }

        val polylines = ArrayList<Polyline>()
        val constellations = ArrayList<Constellation>()
        for (abbr in abbrs) {
            val n = names.getValue(abbr)
            val first = polylines.size
            for ((lineAbbr, ids) in matchedLines) {
                if (lineAbbr != abbr) continue
                val idx = ids.map { indexById.getValue(it) }.toIntArray()
                if (idx.size >= 2) polylines += Polyline(conIndex.getValue(abbr), idx)
            }
            sid(abbr); sid(n.latin); sid(n.genitive); sid(n.fr); sid(n.en)
            constellations += Constellation(
                index = conIndex.getValue(abbr),
                abbreviation = abbr,
                latinName = n.latin,
                genitive = n.genitive,
                nameFr = n.fr,
                nameEn = n.en,
                labelRaRad = Math.toRadians(n.labelRaDeg).toFloat(),
                labelDecRad = Math.toRadians(n.labelDecDeg).toFloat(),
                rank = n.rank,
                firstPolyline = first,
                polylineCount = polylines.size - first,
            )
        }

        // 4. Table des étoiles.
        val n = selected.size
        val ra = FloatArray(n); val dec = FloatArray(n)
        val mag = ShortArray(n); val ci = ShortArray(n)
        val hip = IntArray(n); val hd = IntArray(n)
        val con = ByteArray(n); val bayer = ByteArray(n); val sup = ByteArray(n)
        val flam = ShortArray(n); val name = ShortArray(n)
        val dist = FloatArray(n)
        var namedCount = 0
        var unknownColor = 0
        for ((i, s) in selected.withIndex()) {
            ra[i] = Math.toRadians(s.raHours * 15.0).toFloat()
            dec[i] = Math.toRadians(s.decDeg).toFloat()
            mag[i] = Math.round(s.mag * 1000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            ci[i] = if (s.ci.isNaN()) { unknownColor++; StarTable.COLOR_UNKNOWN } else Math.round(s.ci * 1000.0).toInt().coerceIn(-32000, 32000).toShort()
            hip[i] = s.hip; hd[i] = s.hd
            con[i] = conIndex[s.con]?.toByte() ?: StarTable.NO_CONSTELLATION
            val b = GreekLetters.parseHygToken(s.bayer)
            bayer[i] = (b?.first ?: 0).toByte(); sup[i] = (b?.second ?: 0).toByte()
            flam[i] = s.flam.coerceIn(0, Short.MAX_VALUE.toInt()).toShort()
            if (s.proper.isNotEmpty()) { name[i] = sid(s.proper).toShort(); namedCount++ }
            dist[i] = s.distPc.toFloat()
        }

        val catalog = SkyCatalog(
            buildMagnitudeLimit = magnitudeLimit.toFloat(),
            strings = strings,
            stars = StarTable(ra, dec, mag, ci, hip, hd, con, bayer, sup, flam, name, dist),
            constellations = constellations,
            polylines = polylines,
        )
        val report = buildString {
            appendLine("Catalogue carte-du-ciel")
            appendLine("Source étoiles : ${hygCsv.name} (${hyg.size} lignes)")
            appendLine("Magnitude limite : $magnitudeLimit")
            appendLine("Étoiles retenues : $n (dont ${catalog.countUpToMagnitude(magnitudeLimit)} de magnitude <= $magnitudeLimit, $namedCount avec nom propre, ${lineStarIds.size} dans des figures)")
            appendLine("Indice de couleur inconnu : $unknownColor")
            appendLine("Constellations : ${constellations.size} ; tracés : ${polylines.size} ; sommets : ${polylines.sumOf { it.starIndices.size }}")
            appendLine("Appariement des sommets : écart maximal ${"%.4f".format(java.util.Locale.ROOT, maxSep)}°, sommets hors tolérance (${MATCH_TOLERANCE_DEG}°) : $unmatched")
        }
        return BuildResult(catalog, report, unmatched, maxSep)
    }

    // ---- Lecture des sources -------------------------------------------------------------------

    fun readHyg(file: File): List<HygStar> {
        val out = ArrayList<HygStar>(20000)
        file.bufferedReader().useLines { lines ->
            val it = lines.iterator()
            val header = parseCsvLine(it.next())
            val col = header.withIndex().associate { (i, h) -> h to i }
            fun field(row: List<String>, name: String): String = row.getOrNull(col.getValue(name))?.trim() ?: ""
            while (it.hasNext()) {
                val row = parseCsvLine(it.next())
                if (row.size < header.size) continue
                val id = field(row, "id").toIntOrNull() ?: continue
                if (id == 0) continue
                val mag = field(row, "mag").toDoubleOrNull() ?: continue
                out += HygStar(
                    id = id,
                    hip = field(row, "hip").toIntOrNull() ?: 0,
                    hd = field(row, "hd").toIntOrNull() ?: 0,
                    proper = field(row, "proper"),
                    raHours = field(row, "ra").toDouble(),
                    decDeg = field(row, "dec").toDouble(),
                    mag = mag,
                    ci = field(row, "ci").toDoubleOrNull() ?: Double.NaN,
                    distPc = field(row, "dist").toDoubleOrNull() ?: 0.0,
                    bayer = field(row, "bayer"),
                    flam = field(row, "flam").toIntOrNull() ?: 0,
                    con = field(row, "con"),
                )
            }
        }
        return out
    }

    /** Analyse CSV minimale (guillemets doubles, virgules). */
    fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    class ConstellationNames(val latin: String, val genitive: String, val fr: String, val en: String, val labelRaDeg: Double, val labelDecDeg: Double, val rank: Int)

    fun readConstellationNames(file: File): Map<String, ConstellationNames> {
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val out = LinkedHashMap<String, ConstellationNames>()
        for (f in root.getValue("features").jsonArray) {
            val o = f.jsonObject
            val id = o.getValue("id").jsonPrimitive.content
            if (id in out) continue // Serpens : deux entités « Ser », on garde la première
            val p = o.getValue("properties").jsonObject
            val coords = o.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
            // d3-celestial utilise des espaces typographiques (U+2005) dans certains noms :
            // on les remplace par des espaces ordinaires pour l'affichage et la recherche.
            fun text(key: String) = p.getValue(key).jsonPrimitive.content.replace(Regex("[\\s\\p{Zs}]+"), " ").trim()
            out[id] = ConstellationNames(
                latin = text("la"),
                genitive = text("gen"),
                fr = text("fr"),
                en = text("en"),
                labelRaDeg = coords[0].jsonPrimitive.content.toDouble().let { if (it < 0) it + 360.0 else it },
                labelDecDeg = coords[1].jsonPrimitive.content.toDouble(),
                rank = p["rank"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3,
            )
        }
        return out
    }

    class LineFeature(val abbr: String, val vertices: List<Pair<Double, Double>>)

    fun readLines(file: File): List<LineFeature> {
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val out = ArrayList<LineFeature>()
        for (f in root.getValue("features").jsonArray) {
            val o = f.jsonObject
            val id = o.getValue("id").jsonPrimitive.content
            val geom = o.getValue("geometry").jsonObject
            for (line in geom.getValue("coordinates").jsonArray) {
                val verts = line.jsonArray.map { pt ->
                    val a = pt.jsonArray
                    val lon = a[0].jsonPrimitive.content.toDouble()
                    val ra = if (lon < 0) lon + 360.0 else lon
                    ra to a[1].jsonPrimitive.content.toDouble()
                }
                out += LineFeature(id, verts)
            }
        }
        return out
    }

    /** Recherche du plus proche voisin par tranches de déclinaison. */
    class NearestStarFinder(stars: List<HygStar>) {
        private val buckets = HashMap<Int, MutableList<HygStar>>()
        init {
            for (s in stars) buckets.getOrPut(Math.floor(s.decDeg).toInt()) { ArrayList() }.add(s)
        }

        fun nearest(raDeg: Double, decDeg: Double): Pair<HygStar, Double> {
            var best: HygStar? = null
            var bestSep = Double.MAX_VALUE
            val d0 = Math.floor(decDeg).toInt()
            for (b in d0 - 1..d0 + 1) {
                for (s in buckets[b] ?: continue) {
                    val sep = separationDeg(raDeg, decDeg, s.raHours * 15.0, s.decDeg)
                    if (sep < bestSep) { bestSep = sep; best = s }
                }
            }
            return (best ?: throw IllegalStateException("Aucune étoile près de ($raDeg, $decDeg)")) to bestSep
        }

        private fun separationDeg(ra1: Double, de1: Double, ra2: Double, de2: Double): Double {
            val a1 = Math.toRadians(ra1); val d1 = Math.toRadians(de1)
            val a2 = Math.toRadians(ra2); val d2 = Math.toRadians(de2)
            val c = sin(d1) * sin(d2) + cos(d1) * cos(d2) * cos(a1 - a2)
            return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0)))
        }
    }
}
