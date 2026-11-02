package fr.cartedueciel.astro.math

import kotlin.math.atan
import kotlin.math.tan

/** Point projeté en coordonnées normalisées : x et y dans [−1, 1] à l'intérieur de l'écran. */
data class ScreenPoint(val x: Double, val y: Double) {
    val isOnScreen: Boolean get() = x >= -1.0 && x <= 1.0 && y >= -1.0 && y <= 1.0
}

/**
 * Caméra virtuelle en perspective : matrice de vue (monde → caméra) et champ de vision.
 *
 * @param view matrice de vue (voir [DeviceOrientation.viewMatrix])
 * @param fovYDeg champ de vision vertical en degrés
 * @param aspect rapport largeur / hauteur de la zone d'affichage
 */
class SkyCamera(val view: Mat3, val fovYDeg: Double, val aspect: Double) {
    private val tanHalfY = tan(Math.toRadians(fovYDeg) / 2.0)
    private val tanHalfX = tanHalfY * aspect

    /** Champ de vision horizontal (degrés). */
    val fovXDeg: Double get() = Math.toDegrees(2.0 * atan(tanHalfX))

    /** Direction visée (centre de l'écran) dans le repère monde. */
    val lookDirection: Vec3 get() = DeviceOrientation.lookDirection(view)

    /**
     * Projette une direction du repère monde. Renvoie `null` si elle est derrière la caméra
     * (ou exactement dans le plan de l'image).
     */
    fun project(worldDir: Vec3): ScreenPoint? {
        val c = view * worldDir
        if (c.z >= -1e-9) return null
        return ScreenPoint((c.x / -c.z) / tanHalfX, (c.y / -c.z) / tanHalfY)
    }

    /** Direction (repère monde) correspondant à un point normalisé de l'écran. */
    fun unproject(p: ScreenPoint): Vec3 {
        val c = Vec3(p.x * tanHalfX, p.y * tanHalfY, -1.0).normalized()
        return view.transposed() * c
    }

    /** Distance angulaire (radians) entre une direction et le centre de l'écran. */
    fun angleFromCenter(worldDir: Vec3): Double = lookDirection.angleTo(worldDir)

    /**
     * Matrice de projection OpenGL (column-major, 4×4) pour ce champ de vision. Les objets du
     * ciel étant à l'infini, les plans near/far sont arbitraires.
     */
    fun projectionGl(near: Float = 0.1f, far: Float = 10f): FloatArray {
        val f = (1.0 / tanHalfY).toFloat()
        val out = FloatArray(16)
        out[0] = f / aspect.toFloat()
        out[5] = f
        out[10] = (far + near) / (near - far)
        out[11] = -1f
        out[14] = (2f * far * near) / (near - far)
        return out
    }

    companion object {
        /** Champ de vision vertical par défaut en portrait (degrés), proche d'un objectif grand-angle de téléphone. */
        const val DEFAULT_FOV_Y_DEG = 60.0
        const val MIN_FOV_Y_DEG = 15.0
        const val MAX_FOV_Y_DEG = 110.0
    }
}
