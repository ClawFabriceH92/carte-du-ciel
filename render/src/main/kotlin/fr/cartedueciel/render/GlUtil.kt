package fr.cartedueciel.render

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object GlUtil {
    private const val TAG = "SkyRender"

    fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Échec de compilation du shader : $log")
        }
        return shader
    }

    fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Échec d'édition de liens du programme : $log")
        }
        return program
    }

    fun floatBuffer(data: FloatArray, count: Int = data.size): FloatBuffer =
        ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also {
            it.put(data, 0, count)
            it.position(0)
        }

    fun checkError(where: String) {
        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) Log.w(TAG, "Erreur OpenGL $err dans $where")
    }

    /** Fragment commun : en mode nuit, seule la composante rouge subsiste (aucun pixel blanc). */
    const val NIGHT_SNIPPET = """
        vec4 applyNight(vec4 c) {
            if (uNight > 0.5) {
                float l = max(c.r, max(c.g, c.b));
                return vec4(l, 0.0, 0.0, c.a);
            }
            return c;
        }
    """
}
