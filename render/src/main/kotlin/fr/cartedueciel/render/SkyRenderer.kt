package fr.cartedueciel.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moteur de rendu OpenGL ES 2.0 du ciel : sol, grille et horizon, figures, étoiles, Soleil / Lune /
 * planètes, libellés avec suppression des chevauchements. Mode nuit et fond transparent (caméra)
 * sont gérés dans les shaders et au nettoyage de l'image.
 */
class SkyRenderer(context: Context) : GLSurfaceView.Renderer {

    @Volatile var scene: SceneSnapshot = SceneSnapshot.EMPTY
    @Volatile var viewState: ViewState = ViewState.DEFAULT

    /** Appelé sur le fil OpenGL après chaque image (mesure de fluidité, tests). */
    @Volatile var frameListener: ((frameNanos: Long) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private var width = 1
    private var height = 1

    private lateinit var starProgram: StarProgram
    private lateinit var colorProgram: ColorProgram
    private lateinit var spriteProgram: SpriteProgram
    private lateinit var atlas: TextAtlas
    private lateinit var sprites: SpriteTextures

    private val projection = FloatArray(16)
    private val mvp = FloatArray(16)
    private val clip = FloatArray(4)
    private val tmp4 = FloatArray(4)

    // Tampons réutilisés.
    private var starPos: FloatBuffer? = null
    private var starColor: FloatBuffer? = null
    private var starSize: FloatBuffer? = null
    private var starSource: StarBuffer? = null
    private var lineBuffers = HashMap<LineBatch, FloatBuffer>()
    private var groundBuffer: FloatBuffer? = null
    private var groundVertexCount = 0
    private var quadArray = FloatArray(6 * 4 * 64)
    private val white = floatArrayOf(1f, 1f, 1f, 1f)
    private val tint = FloatArray(4)
    private val placed = ArrayList<FloatArray>(128)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        starProgram = StarProgram()
        colorProgram = ColorProgram()
        spriteProgram = SpriteProgram()
        atlas = TextAtlas(density).also { it.createTexture() }
        sprites = SpriteTextures().also { it.create() }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        buildGround()
        starSource = null
        lineBuffers.clear()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = maxOf(1, w)
        height = maxOf(1, h)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val start = System.nanoTime()
        val s = scene
        val v = viewState
        val night = s.nightMode

        if (s.transparentBackground) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
        } else if (night) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
        } else {
            GLES20.glClearColor(s.skyRgba[0], s.skyRgba[1], s.skyRgba[2], 1f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, v.fovYDeg, aspect, 0.1f, 10f)
        Matrix.multiplyMM(mvp, 0, projection, 0, v.view, 0)

        if (s.showGround) {
            val g = groundBuffer
            if (g != null) {
                val rgba = if (s.transparentBackground) floatArrayOf(s.groundRgba[0], s.groundRgba[1], s.groundRgba[2], s.groundRgba[3] * 0.45f) else s.groundRgba
                colorProgram.draw(mvp, g, groundVertexCount, GLES20.GL_TRIANGLES, rgba, night)
            }
        }

        for (batch in s.lines) drawLines(batch, night)

        drawStars(s, night)
        drawSprites(s, night)
        drawLabels(s, night)
        drawRings(v, night)

        frameListener?.invoke(System.nanoTime() - start)
    }

    // ---- Sol ------------------------------------------------------------------------------------

    private fun buildGround() {
        // Hémisphère inférieur : anneaux de hauteur 0°, −20°, −50°, −90° (nadir), 48 secteurs.
        val alts = floatArrayOf(0f, -20f, -50f, -89.9f)
        val sectors = 48
        val verts = ArrayList<Float>()
        fun pt(azDeg: Float, altDeg: Float) {
            val az = Math.toRadians(azDeg.toDouble()); val alt = Math.toRadians(altDeg.toDouble())
            val c = cos(alt)
            verts += (c * sin(az)).toFloat(); verts += (c * cos(az)).toFloat(); verts += sin(alt).toFloat()
        }
        for (ring in 0 until alts.size - 1) {
            for (i in 0 until sectors) {
                val a0 = 360f * i / sectors
                val a1 = 360f * (i + 1) / sectors
                pt(a0, alts[ring]); pt(a1, alts[ring]); pt(a0, alts[ring + 1])
                pt(a1, alts[ring]); pt(a1, alts[ring + 1]); pt(a0, alts[ring + 1])
            }
        }
        groundVertexCount = verts.size / 3
        groundBuffer = GlUtil.floatBuffer(verts.toFloatArray())
    }

    // ---- Lignes ---------------------------------------------------------------------------------

    private fun drawLines(batch: LineBatch, night: Boolean) {
        if (batch.vertexCount < 2) return
        val buf = lineBuffers.getOrPut(batch) { GlUtil.floatBuffer(batch.xyz, batch.vertexCount * 3) }
        if (lineBuffers.size > 64) lineBuffers.keys.retainAll(scene.lines.toSet())
        GLES20.glLineWidth((batch.widthDp * density).coerceAtLeast(1f))
        val mode = if (batch.mode == LineMode.STRIP) GLES20.GL_LINE_STRIP else GLES20.GL_LINES
        colorProgram.draw(mvp, buf, batch.vertexCount, mode, batch.rgba, night)
    }

    // ---- Étoiles --------------------------------------------------------------------------------

    private fun drawStars(s: SceneSnapshot, night: Boolean) {
        val stars = s.stars ?: return
        if (stars !== starSource) {
            starPos = GlUtil.floatBuffer(stars.xyz, stars.count * 3)
            starColor = GlUtil.floatBuffer(stars.rgb, stars.count * 3)
            starSize = GlUtil.floatBuffer(stars.sizeDp, stars.count)
            starSource = stars
        }
        starProgram.draw(mvp, starPos!!, starColor!!, starSize!!, stars.count, density, night, 1f)
    }

    // ---- Projection écran -----------------------------------------------------------------------

    /** Projette une direction ENU ; renvoie false si elle est derrière la caméra. Résultat dans out[0..1] (pixels). */
    private fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        tmp4[0] = x; tmp4[1] = y; tmp4[2] = z; tmp4[3] = 1f
        Matrix.multiplyMV(clip, 0, mvp, 0, tmp4, 0)
        if (clip[3] <= 1e-6f) return false
        val nx = clip[0] / clip[3]
        val ny = clip[1] / clip[3]
        out[0] = (nx + 1f) * 0.5f * width
        out[1] = (1f - ny) * 0.5f * height
        return true
    }

    // ---- Soleil, Lune, planètes -----------------------------------------------------------------

    private fun drawSprites(s: SceneSnapshot, night: Boolean) {
        if (s.sprites.isEmpty()) return
        val p = FloatArray(2)
        val sunP = FloatArray(2)
        val sunEnu = s.sunEnu
        for (item in s.sprites) {
            if (!project(item.x, item.y, item.z, p)) continue
            val half = item.sizeDp * density / 2f
            if (p[0] < -half || p[0] > width + half || p[1] < -half || p[1] > height + half) continue
            var angle = 0f
            val texture = when (item.kind) {
                SpriteKind.SUN -> sprites.sun
                SpriteKind.PLANET -> sprites.planet
                SpriteKind.MOON -> {
                    sprites.updateMoon(item.phaseFraction)
                    // Oriente le limbe éclairé vers le Soleil : direction Lune → Soleil à l'écran.
                    if (sunEnu != null && project(sunEnu[0], sunEnu[1], sunEnu[2], sunP)) {
                        angle = atan2(sunP[1] - p[1], sunP[0] - p[0])
                    } else if (sunEnu != null) {
                        // Soleil derrière la caméra : on utilise la projection de sa direction opposée.
                        project(-sunEnu[0], -sunEnu[1], -sunEnu[2], sunP)
                        angle = atan2(p[1] - sunP[1], p[0] - sunP[0])
                    }
                    sprites.moon
                }
            }
            tint[0] = item.rgb[0]; tint[1] = item.rgb[1]; tint[2] = item.rgb[2]; tint[3] = 1f
            val n = rotatedQuad(quadArray, 0, p[0], p[1], half, angle)
            spriteProgram.draw(width, height, texture, GlUtil.floatBuffer(quadArray, n * 4), n, tint, night)
        }
    }

    /** Anneaux de mise en évidence : objet visé (petit, discret) et objet recherché (plus grand). */
    private fun drawRings(v: ViewState, night: Boolean) {
        val p = FloatArray(2)
        v.highlightEnu?.let { e ->
            if (project(e[0], e[1], e[2], p)) {
                tint[0] = 1f; tint[1] = 1f; tint[2] = 1f; tint[3] = 0.9f
                val n = rotatedQuad(quadArray, 0, p[0], p[1], 14f * density, 0f)
                spriteProgram.draw(width, height, sprites.ring, GlUtil.floatBuffer(quadArray, n * 4), n, tint, night)
            }
        }
        v.guideEnu?.let { e ->
            if (project(e[0], e[1], e[2], p)) {
                tint[0] = 1f; tint[1] = 0.85f; tint[2] = 0.3f; tint[3] = 1f
                val n = rotatedQuad(quadArray, 0, p[0], p[1], 24f * density, 0f)
                spriteProgram.draw(width, height, sprites.ring, GlUtil.floatBuffer(quadArray, n * 4), n, tint, night)
            }
        }
    }

    /** Écrit un quadrilatère tourné (6 sommets x, y, u, v) et renvoie le nombre de sommets. */
    private fun rotatedQuad(out: FloatArray, offset: Int, cx: Float, cy: Float, half: Float, angle: Float): Int {
        val c = cos(angle); val sn = sin(angle)
        fun corner(dx: Float, dy: Float, u: Float, v: Float, i: Int) {
            val x = cx + dx * c - dy * sn
            val y = cy + dx * sn + dy * c
            val o = offset + i * 4
            out[o] = x; out[o + 1] = y; out[o + 2] = u; out[o + 3] = v
        }
        corner(-half, -half, 0f, 0f, 0); corner(half, -half, 1f, 0f, 1); corner(-half, half, 0f, 1f, 2)
        corner(half, -half, 1f, 0f, 3); corner(half, half, 1f, 1f, 4); corner(-half, half, 0f, 1f, 5)
        return 6
    }

    // ---- Libellés -------------------------------------------------------------------------------

    private fun drawLabels(s: SceneSnapshot, night: Boolean) {
        if (s.labels.isEmpty() || s.labelDensity <= 0f) return
        val maxLabels = (8 + s.labelDensity * 72).toInt()
        placed.clear()
        val p = FloatArray(2)
        var quadCount = 0
        val margin = 4f * density
        val sorted = s.labels.sortedBy { it.priority }
        for (label in sorted) {
            if (placed.size >= maxLabels) break
            if (!project(label.x, label.y, label.z, p)) continue
            if (p[0] < -50 || p[0] > width + 50 || p[1] < -50 || p[1] > height + 50) continue
            val g = atlas.get(label.text, label.style)
            if (g.widthPx == 0) continue
            val w = g.widthPx.toFloat(); val h = g.heightPx.toFloat()
            val (x0, y0) = when (label.style) {
                LabelStyle.STAR -> (p[0] + 6f * density) to (p[1] - h * 0.85f)
                LabelStyle.BODY -> (p[0] - w / 2f) to (p[1] + 10f * density)
                LabelStyle.CONSTELLATION, LabelStyle.CARDINAL -> (p[0] - w / 2f) to (p[1] - h / 2f)
            }
            val rect = floatArrayOf(x0 - margin, y0 - margin, x0 + w + margin, y0 + h + margin)
            if (rect[2] < 0f || rect[0] > width || rect[3] < 0f || rect[1] > height) continue
            var overlaps = false
            for (r in placed) {
                if (rect[0] < r[2] && rect[2] > r[0] && rect[1] < r[3] && rect[3] > r[1]) { overlaps = true; break }
            }
            if (overlaps) continue
            placed += rect
            if ((quadCount + 6) * 4 > quadArray.size) quadArray = quadArray.copyOf(quadArray.size * 2)
            val o = quadCount * 4
            val x1 = x0 + w; val y1 = y0 + h
            val q = quadArray
            q[o] = x0; q[o + 1] = y0; q[o + 2] = g.u0; q[o + 3] = g.v0
            q[o + 4] = x1; q[o + 5] = y0; q[o + 6] = g.u1; q[o + 7] = g.v0
            q[o + 8] = x0; q[o + 9] = y1; q[o + 10] = g.u0; q[o + 11] = g.v1
            q[o + 12] = x1; q[o + 13] = y0; q[o + 14] = g.u1; q[o + 15] = g.v0
            q[o + 16] = x1; q[o + 17] = y1; q[o + 18] = g.u1; q[o + 19] = g.v1
            q[o + 20] = x0; q[o + 21] = y1; q[o + 22] = g.u0; q[o + 23] = g.v1
            quadCount += 6
        }
        if (quadCount == 0) return
        atlas.upload()
        val alpha = 0.55f + 0.45f * s.labelDensity
        tint[0] = 1f; tint[1] = 1f; tint[2] = 1f; tint[3] = alpha
        spriteProgram.draw(width, height, atlas.texture, GlUtil.floatBuffer(quadArray, quadCount * 4), quadCount, tint, night)
    }

    companion object {
        /** Norme d'un vecteur (utilitaire pour les appelants qui construisent la scène). */
        fun length(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)
    }
}
