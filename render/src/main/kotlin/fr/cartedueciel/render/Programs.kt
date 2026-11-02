package fr.cartedueciel.render

import android.opengl.GLES20
import java.nio.FloatBuffer

/** Points (étoiles) : taille et couleur par sommet, disque adouci. */
internal class StarProgram {
    private val program = GlUtil.linkProgram(
        """
        uniform mat4 uMvp;
        uniform float uSizeScale;
        attribute vec3 aPos;
        attribute vec3 aColor;
        attribute float aSize;
        varying vec3 vColor;
        varying float vSize;
        void main() {
            gl_Position = uMvp * vec4(aPos, 1.0);
            gl_PointSize = aSize * uSizeScale;
            vColor = aColor;
            vSize = aSize * uSizeScale;
        }
        """,
        """
        precision mediump float;
        uniform float uNight;
        uniform float uAlpha;
        varying vec3 vColor;
        varying float vSize;
        ${GlUtil.NIGHT_SNIPPET}
        void main() {
            vec2 d = gl_PointCoord - vec2(0.5);
            float r = length(d) * 2.0;
            if (r > 1.0) discard;
            float core = 1.0 - smoothstep(0.55, 1.0, r);
            float glow = (1.0 - r) * 0.35;
            float a = clamp(core + glow, 0.0, 1.0) * uAlpha;
            gl_FragColor = applyNight(vec4(vColor, a));
        }
        """,
    )
    private val uMvp = GLES20.glGetUniformLocation(program, "uMvp")
    private val uSizeScale = GLES20.glGetUniformLocation(program, "uSizeScale")
    private val uNight = GLES20.glGetUniformLocation(program, "uNight")
    private val uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
    private val aPos = GLES20.glGetAttribLocation(program, "aPos")
    private val aColor = GLES20.glGetAttribLocation(program, "aColor")
    private val aSize = GLES20.glGetAttribLocation(program, "aSize")

    fun draw(mvp: FloatArray, pos: FloatBuffer, color: FloatBuffer, size: FloatBuffer, count: Int, sizeScale: Float, night: Boolean, alpha: Float) {
        if (count <= 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(uSizeScale, sizeScale)
        GLES20.glUniform1f(uNight, if (night) 1f else 0f)
        GLES20.glUniform1f(uAlpha, alpha)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, pos)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 0, color)
        GLES20.glEnableVertexAttribArray(aSize)
        GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, 0, size)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aColor)
        GLES20.glDisableVertexAttribArray(aSize)
    }
}

/** Géométrie 3D de couleur uniforme : lignes (figures, horizon, grille) et triangles (sol). */
internal class ColorProgram {
    private val program = GlUtil.linkProgram(
        """
        uniform mat4 uMvp;
        attribute vec3 aPos;
        void main() { gl_Position = uMvp * vec4(aPos, 1.0); }
        """,
        """
        precision mediump float;
        uniform float uNight;
        uniform vec4 uColor;
        ${GlUtil.NIGHT_SNIPPET}
        void main() { gl_FragColor = applyNight(uColor); }
        """,
    )
    private val uMvp = GLES20.glGetUniformLocation(program, "uMvp")
    private val uNight = GLES20.glGetUniformLocation(program, "uNight")
    private val uColor = GLES20.glGetUniformLocation(program, "uColor")
    private val aPos = GLES20.glGetAttribLocation(program, "aPos")

    fun draw(mvp: FloatArray, pos: FloatBuffer, count: Int, glMode: Int, rgba: FloatArray, night: Boolean) {
        if (count <= 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(uNight, if (night) 1f else 0f)
        GLES20.glUniform4fv(uColor, 1, rgba, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, pos)
        GLES20.glDrawArrays(glMode, 0, count)
        GLES20.glDisableVertexAttribArray(aPos)
    }
}

/** Quadrilatères texturés en coordonnées écran (pixels, origine en haut à gauche) : libellés, Soleil, Lune, planètes. */
internal class SpriteProgram {
    private val program = GlUtil.linkProgram(
        """
        uniform vec2 uViewport;
        attribute vec2 aPos;
        attribute vec2 aUv;
        varying vec2 vUv;
        void main() {
            vec2 ndc = vec2(aPos.x / uViewport.x * 2.0 - 1.0, 1.0 - aPos.y / uViewport.y * 2.0);
            gl_Position = vec4(ndc, 0.0, 1.0);
            vUv = aUv;
        }
        """,
        """
        precision mediump float;
        uniform float uNight;
        uniform sampler2D uTex;
        uniform vec4 uTint;
        varying vec2 vUv;
        ${GlUtil.NIGHT_SNIPPET}
        void main() {
            vec4 t = texture2D(uTex, vUv);
            gl_FragColor = applyNight(vec4(t.rgb * uTint.rgb, t.a * uTint.a));
        }
        """,
    )
    private val uViewport = GLES20.glGetUniformLocation(program, "uViewport")
    private val uNight = GLES20.glGetUniformLocation(program, "uNight")
    private val uTex = GLES20.glGetUniformLocation(program, "uTex")
    private val uTint = GLES20.glGetUniformLocation(program, "uTint")
    private val aPos = GLES20.glGetAttribLocation(program, "aPos")
    private val aUv = GLES20.glGetAttribLocation(program, "aUv")

    /** `quads` : par sommet x, y, u, v ; 6 sommets par quadrilatère. */
    fun draw(width: Int, height: Int, texture: Int, quads: FloatBuffer, vertexCount: Int, tint: FloatArray, night: Boolean) {
        if (vertexCount <= 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(uViewport, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(uNight, if (night) 1f else 0f)
        GLES20.glUniform4fv(uTint, 1, tint, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        quads.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quads)
        GLES20.glEnableVertexAttribArray(aUv)
        quads.position(2)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quads)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
        quads.position(0)
    }
}
