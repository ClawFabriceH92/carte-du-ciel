package fr.cartedueciel.render

/**
 * Description de ce que le moteur de rendu doit dessiner. Les instances sont immuables et
 * remplacées d'un bloc (environ une fois par seconde pour la scène, à chaque échantillon de
 * capteur pour la vue), ce qui évite tout verrou entre le fil des capteurs et le fil OpenGL.
 *
 * Toutes les positions sont des directions unitaires dans le repère ENU (x = est, y = nord,
 * z = zénith), réfraction déjà appliquée.
 */
class StarBuffer(
    /** x, y, z entrelacés. */
    val xyz: FloatArray,
    /** r, g, b entrelacés dans [0, 1]. */
    val rgb: FloatArray,
    /** Diamètre du point en pixels indépendants de la densité (dp). */
    val sizeDp: FloatArray,
    /** Nombre d'étoiles à dessiner (préfixe des tableaux). */
    val count: Int,
)

enum class LineMode { SEGMENTS, STRIP }

class LineBatch(
    val xyz: FloatArray,
    val vertexCount: Int,
    val rgba: FloatArray,
    val widthDp: Float,
    val mode: LineMode = LineMode.SEGMENTS,
)

enum class SpriteKind { SUN, MOON, PLANET }

class SpriteItem(
    val x: Float,
    val y: Float,
    val z: Float,
    val kind: SpriteKind,
    /** Diamètre en dp. */
    val sizeDp: Float,
    val rgb: FloatArray,
    /** Lune : fraction éclairée (0 à 1) ; sinon ignoré. */
    val phaseFraction: Float = 1f,
)

enum class LabelStyle { STAR, CONSTELLATION, BODY, CARDINAL }

class SkyLabel(
    val x: Float,
    val y: Float,
    val z: Float,
    val text: String,
    val style: LabelStyle,
    /** Priorité croissante = moins important (une magnitude, par exemple). */
    val priority: Float,
)

class SceneSnapshot(
    val stars: StarBuffer?,
    val lines: List<LineBatch>,
    val sprites: List<SpriteItem>,
    val labels: List<SkyLabel>,
    /** Couleur du fond de ciel (ignorée si [transparentBackground]). */
    val skyRgba: FloatArray,
    /** Couleur du sol (sous l'horizon). */
    val groundRgba: FloatArray,
    val showGround: Boolean,
    /** Mode nuit : tout est rendu en rouge sur noir. */
    val nightMode: Boolean,
    /** Fond caméra : le ciel est dessiné sur fond transparent. */
    val transparentBackground: Boolean,
    /** Densité de libellés, 0 (aucun) à 1 (maximum). */
    val labelDensity: Float,
    /** Direction ENU du Soleil, pour orienter la phase de la Lune. */
    val sunEnu: FloatArray?,
) {
    companion object {
        val EMPTY = SceneSnapshot(
            stars = null, lines = emptyList(), sprites = emptyList(), labels = emptyList(),
            skyRgba = floatArrayOf(0f, 0f, 0.03f, 1f), groundRgba = floatArrayOf(0.04f, 0.06f, 0.04f, 0.85f),
            showGround = true, nightMode = false, transparentBackground = false, labelDensity = 0.6f, sunEnu = null,
        )
    }
}

/**
 * Vue courante : matrice de vue (ENU → caméra, 4×4 column-major), champ vertical, et
 * éventuellement l'objet visé à mettre en évidence (anneau) et l'objet recherché (anneau de guidage).
 */
class ViewState(
    val view: FloatArray,
    val fovYDeg: Float,
    /** Direction ENU de l'objet au réticule, ou null. */
    val highlightEnu: FloatArray? = null,
    /** Direction ENU de l'objet recherché, ou null. */
    val guideEnu: FloatArray? = null,
) {
    companion object {
        val DEFAULT = ViewState(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            60f,
        )
    }
}
