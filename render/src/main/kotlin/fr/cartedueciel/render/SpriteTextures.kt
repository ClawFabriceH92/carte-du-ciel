package fr.cartedueciel.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.abs

/** Textures procédurales du Soleil, de la Lune (avec phase) et des planètes. À utiliser sur le fil OpenGL. */
internal class SpriteTextures {
    var sun = 0; private set
    var planet = 0; private set
    var moon = 0; private set
    private var moonFraction = -1f

    fun create() {
        sun = upload(sunBitmap(), 0)
        planet = upload(planetBitmap(), 0)
        moon = upload(moonBitmap(1f), 0)
        moonFraction = 1f
    }

    /** Met à jour la texture lunaire si la fraction éclairée a changé de plus de 1 %. */
    fun updateMoon(fraction: Float) {
        if (abs(fraction - moonFraction) < 0.01f) return
        moonFraction = fraction
        moon = upload(moonBitmap(fraction), moon)
    }

    private fun upload(bitmap: Bitmap, existing: Int): Int {
        val id = if (existing != 0) existing else IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return id
    }

    private fun sunBitmap(): Bitmap {
        val s = SIZE
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val r = s / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            r, r, r,
            intArrayOf(Color.WHITE, Color.rgb(255, 245, 200), Color.argb(160, 255, 220, 120), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(r, r, r, p)
        return b
    }

    private fun planetBitmap(): Bitmap {
        val s = SIZE
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val r = s / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            r, r, r,
            intArrayOf(Color.WHITE, Color.WHITE, Color.argb(90, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(r, r, r, p)
        return b
    }

    /**
     * Disque lunaire : partie sombre grise, partie éclairée claire. Le limbe éclairé est à droite
     * (+x) ; le moteur de rendu oriente ensuite le sprite vers le Soleil. Le terminateur est une
     * demi-ellipse de demi-axe horizontal R·|1 − 2k| (k = fraction éclairée).
     */
    private fun moonBitmap(fraction: Float): Bitmap {
        val s = SIZE
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val cx = s / 2f
        val cy = s / 2f
        val r = s / 2f - 4f
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 72, 78) }
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 235, 225) }
        c.drawCircle(cx, cy, r, dark)
        val k = fraction.coerceIn(0f, 1f)
        val rightHalf = Path().apply { addArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f); lineTo(cx, cy + r); close() }
        val a = r * abs(1f - 2f * k)
        val ellipse = Path().apply { addOval(RectF(cx - a, cy - r, cx + a, cy + r), Path.Direction.CW) }
        val litPath = Path(rightHalf)
        if (k >= 0.5f) litPath.op(ellipse, Path.Op.UNION) else litPath.op(ellipse, Path.Op.DIFFERENCE)
        c.drawPath(litPath, lit)
        return b
    }

    companion object {
        const val SIZE = 128
    }
}
