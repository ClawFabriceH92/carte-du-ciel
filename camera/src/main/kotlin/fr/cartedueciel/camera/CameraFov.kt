package fr.cartedueciel.camera

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.atan

/**
 * Champ de vision estimé de la caméra arrière (degrés), d'après la focale et la taille physique
 * du capteur annoncées par Camera2 : fov = 2·atan(taille / (2·focale)).
 *
 * @property longSideDeg champ selon le grand côté du capteur (vertical quand le téléphone est en portrait)
 * @property shortSideDeg champ selon le petit côté du capteur
 *
 * Limites connues (cahier des charges, section 4) : la prévisualisation est recadrée pour remplir
 * l'écran et certains appareils annoncent des valeurs approximatives ; la méthode doit être validée
 * sur plusieurs modèles. L'application prévoit un facteur de correction manuel.
 */
data class CameraFov(val longSideDeg: Double, val shortSideDeg: Double) {
    companion object {
        @OptIn(ExperimentalCamera2Interop::class)
        fun estimate(cameraInfo: CameraInfo): CameraFov? {
            return try {
                val c2 = Camera2CameraInfo.from(cameraInfo)
                val focals = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return null
                val size = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
                if (focals.isEmpty()) return null
                val f = focals.minOrNull()!!.toDouble()
                val w = size.width.toDouble()
                val h = size.height.toDouble()
                val long = maxOf(w, h)
                val short = minOf(w, h)
                if (f <= 0.0 || long <= 0.0) return null
                CameraFov(
                    longSideDeg = Math.toDegrees(2.0 * atan(long / (2.0 * f))),
                    shortSideDeg = Math.toDegrees(2.0 * atan(short / (2.0 * f))),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
