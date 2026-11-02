package fr.cartedueciel.render

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * Vue OpenGL du ciel. Le fond est transparent (format RGBA 8888) afin de pouvoir se superposer
 * à la prévisualisation caméra placée dessous ; `setZOrderMediaOverlay(true)` place la surface
 * au-dessus de celle de la caméra mais sous l'interface Compose.
 */
class SkyGLSurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    val renderer: SkyRenderer = SkyRenderer(context)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setScene(scene: SceneSnapshot) {
        renderer.scene = scene
    }

    fun setView(view: ViewState) {
        renderer.viewState = view
    }
}
