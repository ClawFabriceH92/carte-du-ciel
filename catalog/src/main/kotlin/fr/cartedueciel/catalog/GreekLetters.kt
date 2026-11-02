package fr.cartedueciel.catalog

/** Lettres grecques des désignations de Bayer, dans l'ordre alphabétique grec (index 1 à 24). */
object GreekLetters {
    data class Letter(val index: Int, val hygToken: String, val symbol: String, val name: String)

    val letters: List<Letter> = listOf(
        Letter(1, "Alp", "α", "alpha"), Letter(2, "Bet", "β", "beta"), Letter(3, "Gam", "γ", "gamma"),
        Letter(4, "Del", "δ", "delta"), Letter(5, "Eps", "ε", "epsilon"), Letter(6, "Zet", "ζ", "zeta"),
        Letter(7, "Eta", "η", "eta"), Letter(8, "The", "θ", "theta"), Letter(9, "Iot", "ι", "iota"),
        Letter(10, "Kap", "κ", "kappa"), Letter(11, "Lam", "λ", "lambda"), Letter(12, "Mu", "μ", "mu"),
        Letter(13, "Nu", "ν", "nu"), Letter(14, "Xi", "ξ", "xi"), Letter(15, "Omi", "ο", "omicron"),
        Letter(16, "Pi", "π", "pi"), Letter(17, "Rho", "ρ", "rho"), Letter(18, "Sig", "σ", "sigma"),
        Letter(19, "Tau", "τ", "tau"), Letter(20, "Ups", "υ", "upsilon"), Letter(21, "Phi", "φ", "phi"),
        Letter(22, "Chi", "χ", "chi"), Letter(23, "Psi", "ψ", "psi"), Letter(24, "Ome", "ω", "omega"),
    )

    private val byToken = letters.associateBy { it.hygToken }
    private val byIndex = letters.associateBy { it.index }

    /** Index (1..24) et exposant (0 si absent) d'un jeton HYG tel que « Alp », « Alp-2 », « Mu ». */
    fun parseHygToken(token: String): Pair<Int, Int>? {
        val t = token.trim()
        if (t.isEmpty()) return null
        val parts = t.split('-')
        val letter = byToken[parts[0]] ?: return null
        val sup = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return letter.index to sup
    }

    fun symbol(index: Int): String = byIndex[index]?.symbol ?: ""
    fun name(index: Int): String = byIndex[index]?.name ?: ""

    private val superscripts = arrayOf("", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹")

    /** Exposant typographique (« ² ») pour les désignations multiples. */
    fun superscript(n: Int): String = superscripts.getOrElse(n) { n.toString() }
}
