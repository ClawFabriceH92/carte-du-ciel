package fr.cartedueciel.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import fr.cartedueciel.astro.math.Mat3
import fr.cartedueciel.astro.math.Quaternion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Capteur utilisé pour l'orientation. */
enum class OrientationSource {
    /** `TYPE_ROTATION_VECTOR` : fusion gyroscope + accéléromètre + magnétomètre (cas nominal). */
    ROTATION_VECTOR,
    /** `TYPE_GEOMAGNETIC_ROTATION_VECTOR` : sans gyroscope, moins précis ; lissage renforcé. */
    GEOMAGNETIC_ROTATION_VECTOR,
    /** Aucun capteur exploitable : l'application bascule en mode manuel. */
    NONE,
}

/** Qualité estimée de la boussole, affichée en vert / orange / rouge. */
enum class CompassQuality { GOOD, MEDIUM, POOR, UNKNOWN }

/**
 * Échantillon d'orientation.
 *
 * @property deviceToWorld matrice (repère appareil → repère monde magnétique) d'Android, lissée
 * @property headingAccuracyRad précision estimée du cap en radians (cinquième valeur du capteur
 *   de rotation, API 18+), ou −1 si indisponible
 * @property sensorStatus `SensorManager.SENSOR_STATUS_*` (dernier `onAccuracyChanged`)
 * @property timestampNanos horodatage du capteur
 */
data class OrientationSample(
    val deviceToWorld: Mat3,
    val headingAccuracyRad: Float,
    val sensorStatus: Int,
    val timestampNanos: Long,
    val source: OrientationSource,
) {
    val compassQuality: CompassQuality
        get() = when {
            headingAccuracyRad >= 0f -> {
                val deg = Math.toDegrees(headingAccuracyRad.toDouble())
                when {
                    deg <= GOOD_HEADING_DEG -> CompassQuality.GOOD
                    deg <= MEDIUM_HEADING_DEG -> CompassQuality.MEDIUM
                    else -> CompassQuality.POOR
                }
            }
            else -> when (sensorStatus) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassQuality.GOOD
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassQuality.MEDIUM
                SensorManager.SENSOR_STATUS_ACCURACY_LOW, SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassQuality.POOR
                else -> CompassQuality.UNKNOWN
            }
        }

    companion object {
        const val GOOD_HEADING_DEG = 5.0
        const val MEDIUM_HEADING_DEG = 15.0
    }
}

/**
 * Lecture et lissage de l'orientation du téléphone.
 *
 * - Capteur principal `TYPE_ROTATION_VECTOR`, repli `TYPE_GEOMAGNETIC_ROTATION_VECTOR`, sinon aucun.
 * - Cadence `SENSOR_DELAY_GAME` (≈ 50 Hz), donc sans permission `HIGH_SAMPLING_RATE_SENSORS`.
 * - Lissage par interpolation sphérique des quaternions : à chaque échantillon,
 *   `q = slerp(q_précédent, q_mesuré, alpha)` avec `alpha` dans ]0, 1] (1 = aucun lissage).
 */
class OrientationProvider(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val geomagneticRotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    /** Source retenue sur cet appareil. */
    val source: OrientationSource = when {
        rotationVector != null -> OrientationSource.ROTATION_VECTOR
        geomagneticRotationVector != null -> OrientationSource.GEOMAGNETIC_ROTATION_VECTOR
        else -> OrientationSource.NONE
    }

    val isAvailable: Boolean get() = source != OrientationSource.NONE

    /**
     * Flux d'orientations lissées. Le capteur est enregistré à la collecte et libéré à l'annulation,
     * ce qui coupe les capteurs dès que l'application passe en arrière-plan si la collecte est liée
     * au cycle de vie.
     *
     * @param smoothing intensité du lissage dans [0, 1] : 0 = brut, 1 = très lissé
     */
    fun orientations(smoothing: Float): Flow<OrientationSample> = callbackFlow {
        val sensor = when (source) {
            OrientationSource.ROTATION_VECTOR -> rotationVector
            OrientationSource.GEOMAGNETIC_ROTATION_VECTOR -> geomagneticRotationVector
            OrientationSource.NONE -> null
        }
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        // Sans gyroscope, le signal est plus bruité : lissage renforcé (cahier des charges, section 4).
        val alpha = alphaFor(smoothing, source)
        var previous: Quaternion? = null
        var status = SensorManager.SENSOR_STATUS_UNRELIABLE

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val measured = Quaternion.fromAndroidRotationVector(event.values)
                val smoothed = previous?.slerp(measured, alpha) ?: measured
                previous = smoothed
                val heading = if (event.values.size >= 5) event.values[4] else -1f
                trySendBlocking(
                    OrientationSample(
                        deviceToWorld = smoothed.toMat3(),
                        headingAccuracyRad = heading,
                        sensorStatus = status,
                        timestampNanos = event.timestamp,
                        source = source,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                status = accuracy
            }
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    companion object {
        /** Coefficient d'interpolation : 1 (brut) à 0,1 (très lissé), divisé par deux sans gyroscope. */
        fun alphaFor(smoothing: Float, source: OrientationSource): Double {
            val s = smoothing.coerceIn(0f, 1f).toDouble()
            val base = 1.0 - 0.9 * s
            return if (source == OrientationSource.GEOMAGNETIC_ROTATION_VECTOR) (base * 0.5).coerceAtLeast(0.05) else base
        }
    }
}
