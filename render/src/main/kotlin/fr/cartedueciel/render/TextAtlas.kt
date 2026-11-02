package fr.cartedueciel.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils

/** Région d'un texte dans l'atlas. */
internal class Glyph(val u0: Float, val v0: Float, val u1: Float, val v1: Float, val widthPx: Int, val heightPx: Int)

/**
 * Atlas de textes : chaque libellé est dessiné une fois avec `Canvas` (blanc avec ombre sombre,
 * pour rester lisible sur l'image caméra) et empilé dans une texture par « étagères ». Quand
 * l'atlas est plein, il est vidé et reconstruit : cela ne se produit que si le vocabulaire des
 * libellés affichés change beaucoup.
 *
 * À n'utiliser que depuis le fil OpenGL.
 */
internal class TextAtlas(private val density: Float, private val size: Int = 1024) {
    private val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val glyphs = HashMap<String, Glyph>()
    private var shelfY = 0
    private var shelfX = 0
    private var shelfHeight = 0
    private var dirty = false
    var texture = 0
        private set

    private val paints: Map<LabelStyle, Paint> = LabelStyle.entries.associateWith { style -> makePaint(style) }

    private fun makePaint(style: LabelStyle): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = density * when (style) {
            LabelStyle.STAR -> 13f
            LabelStyle.CONSTELLATION -> 15f
            LabelStyle.BODY -> 14f
            LabelStyle.CARDINAL -> 17f
        }
        typeface = when (style) {
            LabelStyle.CONSTELLATION -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            LabelStyle.BODY, LabelStyle.CARDINAL -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            else -> Typeface.SANS_SERIF
        }
        if (style == LabelStyle.CONSTELLATION) letterSpacing = 0.12f
        setShadowLayer(density * 2f, 0f, 0f, Color.argb(220, 0, 0, 0))
    }

    fun createTexture() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        dirty = false
    }

    /** Glyphe d'un texte (dessiné à la demande). */
    fun get(text: String, style: LabelStyle): Glyph {
        val key = style.ordinal.toString() + "|" + text
        glyphs[key]?.let { return it }
        val paint = paints.getValue(style)
        val pad = (density * 3f).toInt()
        val fm = paint.fontMetricsInt
        val w = (paint.measureText(text) + 0.5f).toInt() + 2 * pad
        val h = (fm.descent - fm.ascent) + 2 * pad
        if (w > size) return Glyph(0f, 0f, 0f, 0f, 0, 0)
        if (shelfX + w > size) {
            shelfY += shelfHeight
            shelfX = 0
            shelfHeight = 0
        }
        if (shelfY + h > size) {
            // Atlas plein : on repart de zéro.
            glyphs.clear()
            bitmap.eraseColor(Color.TRANSPARENT)
            shelfX = 0; shelfY = 0; shelfHeight = 0
        }
        val x = shelfX
        val y = shelfY
        canvas.drawText(text, (x + pad).toFloat(), (y + pad - fm.ascent).toFloat(), paint)
        shelfX += w
        if (h > shelfHeight) shelfHeight = h
        dirty = true
        val g = Glyph(x / size.toFloat(), y / size.toFloat(), (x + w) / size.toFloat(), (y + h) / size.toFloat(), w, h)
        glyphs[key] = g
        return g
    }

    /** Téléverse la texture si des textes ont été ajoutés. */
    fun upload() {
        if (!dirty) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        dirty = false
    }
}
