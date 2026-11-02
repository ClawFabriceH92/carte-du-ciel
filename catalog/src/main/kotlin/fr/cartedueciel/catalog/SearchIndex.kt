package fr.cartedueciel.catalog

import fr.cartedueciel.astro.sky.SolarBody
import java.text.Normalizer

/** Cible d'une recherche. */
sealed class SearchTarget {
    data class StarTarget(val index: Int) : SearchTarget()
    data class ConstellationTarget(val index: Int) : SearchTarget()
    data class BodyTarget(val body: SolarBody) : SearchTarget()
}

/** Suggestion affichée à l'utilisateur. */
data class SearchResult(val label: String, val detail: String, val target: SearchTarget)

/**
 * Index de recherche par nom : étoiles nommées et désignations de Bayer, constellations
 * (français, anglais, latin, abréviation), Soleil, Lune et planètes. Insensible à la casse et
 * aux accents ; les résultats exacts passent avant les préfixes, puis les sous-chaînes ; à
 * égalité, les objets les plus brillants ou les plus connus d'abord.
 */
class SearchIndex(private val catalog: SkyCatalog, private val language: String = "fr") {

    private class Entry(val key: String, val result: SearchResult, val priority: Double)

    private val entries: List<Entry> = buildEntries()

    private fun buildEntries(): List<Entry> {
        val out = ArrayList<Entry>()
        for (body in SolarBody.entries) {
            val label = if (language == "fr") body.nameFr else body.nameEn
            val other = if (language == "fr") body.nameEn else body.nameFr
            val detail = when {
                body == SolarBody.SUN -> if (language == "fr") "Étoile" else "Star"
                body == SolarBody.MOON -> if (language == "fr") "Satellite de la Terre" else "Earth's satellite"
                else -> if (language == "fr") "Planète" else "Planet"
            }
            val r = SearchResult(label, detail, SearchTarget.BodyTarget(body))
            out += Entry(normalize(label), r, -30.0)
            if (!other.equals(label, ignoreCase = true)) out += Entry(normalize(other), r, -30.0)
        }
        for (c in catalog.constellations) {
            val label = if (language == "fr") c.nameFr else c.nameEn
            val detail = (if (language == "fr") "Constellation · " else "Constellation · ") + c.latinName + " (" + c.abbreviation + ")"
            val r = SearchResult(label, detail, SearchTarget.ConstellationTarget(c.index))
            val prio = -20.0 + c.rank
            out += Entry(normalize(label), r, prio)
            out += Entry(normalize(c.latinName), r, prio)
            out += Entry(normalize(c.nameEn), r, prio)
            out += Entry(normalize(c.nameFr), r, prio)
            out += Entry(normalize(c.abbreviation), r, prio)
            out += Entry(normalize(c.genitive), r, prio + 0.5)
        }
        val stars = catalog.stars
        for (i in 0 until stars.size) {
            val name = stars.properNameId[i].toInt().let { if (it > 0) catalog.strings[it] else null }
            val bayer = stars.bayer[i].toInt()
            if (name == null && bayer <= 0) continue
            val star = catalog.star(i)
            val con = star.constellation
            val detail = buildString {
                append(if (language == "fr") "Étoile" else "Star")
                if (con != null) append(" · ").append(if (language == "fr") con.nameFr else con.nameEn)
                append(" · mag %.1f".format(java.util.Locale.ROOT, star.magnitude))
            }
            val r = SearchResult(star.displayName, detail, SearchTarget.StarTarget(i))
            val prio = star.magnitude
            if (name != null) out += Entry(normalize(name), r, prio)
            if (bayer > 0 && con != null) {
                val sup = stars.bayerSuperscript[i].toInt()
                val supText = if (sup > 0) sup.toString() else ""
                val letterName = GreekLetters.name(bayer) + supText
                val symbol = GreekLetters.symbol(bayer) + GreekLetters.superscript(sup)
                val token = GreekLetters.letters[bayer - 1].hygToken + supText
                out += Entry(normalize("$letterName ${con.genitive}"), r, prio + 1.0)
                out += Entry(normalize("$letterName ${con.abbreviation}"), r, prio + 1.0)
                out += Entry(normalize("$symbol ${con.abbreviation}"), r, prio + 1.0)
                out += Entry(normalize("$token ${con.abbreviation}"), r, prio + 1.0)
            }
        }
        return out
    }

    /** Suggestions pour une saisie partielle (au plus `limit`). */
    fun search(query: String, limit: Int = 12): List<SearchResult> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        data class Scored(val entry: Entry, val rank: Int)
        val scored = ArrayList<Scored>()
        for (e in entries) {
            val rank = when {
                e.key == q -> 0
                e.key.startsWith(q) -> 1
                e.key.split(' ').any { it.startsWith(q) } -> 2
                e.key.contains(q) -> 3
                else -> continue
            }
            scored += Scored(e, rank)
        }
        return scored
            .sortedWith(compareBy<Scored> { it.rank }.thenBy { it.entry.priority }.thenBy { it.entry.key })
            .map { it.entry.result }
            .distinctBy { it.target }
            .take(limit)
    }

    companion object {
        /** Minuscules, sans accents, espaces normalisés. */
        fun normalize(s: String): String {
            val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
            val sb = StringBuilder(decomposed.length)
            var lastSpace = true
            for (ch in decomposed) {
                val type = Character.getType(ch)
                if (type == Character.NON_SPACING_MARK.toInt()) continue
                val c = when {
                    ch.isLetterOrDigit() -> ch.lowercaseChar()
                    ch == '¹' -> '1'; ch == '²' -> '2'; ch == '³' -> '3'
                    else -> ' '
                }
                if (c == ' ') {
                    if (!lastSpace) sb.append(' ')
                    lastSpace = true
                } else {
                    sb.append(c); lastSpace = false
                }
            }
            return sb.toString().trim()
        }
    }
}
