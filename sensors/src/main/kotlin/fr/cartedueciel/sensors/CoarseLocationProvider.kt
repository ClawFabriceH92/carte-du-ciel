package fr.cartedueciel.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import fr.cartedueciel.astro.sky.GeoPosition
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Localisation approximative via `LocationManager` (sans services Google), suffisante pour le
 * calcul du ciel (cahier des charges, section 5 : une erreur de 1 km déplace le zénith de 0,009°).
 * Seule la permission `ACCESS_COARSE_LOCATION` est requise.
 */
class CoarseLocationProvider(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Dernière position connue, tous fournisseurs confondus, la plus récente d'abord. */
    fun lastKnown(): GeoPosition? {
        if (!hasPermission) return null
        return try {
            locationManager.allProviders
                .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.toGeoPosition()
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Demande une position unique (délai maximal `timeoutMs`), d'abord au fournisseur réseau puis
     * au GPS. Renvoie `null` en cas d'échec ou d'absence de permission.
     */
    suspend fun requestOnce(timeoutMs: Long = 15_000L): GeoPosition? {
        if (!hasPermission) return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        for (provider in providers) {
            val result = withTimeoutOrNull(timeoutMs) { requestFrom(provider) }
            if (result != null) return result
        }
        return lastKnown()
    }

    private suspend fun requestFrom(provider: String): GeoPosition? = suspendCancellableCoroutine { cont ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                val executor = Executor { r -> r.run() }
                locationManager.getCurrentLocation(provider, signal, executor) { loc: Location? ->
                    if (cont.isActive) cont.resume(loc?.toGeoPosition())
                }
                cont.invokeOnCancellation { signal.cancel() }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location.toGeoPosition())
                    }
                    // Abstraite jusqu'à l'API 28, dépréciée depuis l'API 29 : l'implémentation vide reste nécessaire pour minSdk 26.
                    @Deprecated("Deprecated in Java")
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
            }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        } catch (e: IllegalArgumentException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun Location.toGeoPosition(): GeoPosition = GeoPosition(
        latitudeDeg = latitude.coerceIn(-90.0, 90.0),
        longitudeDeg = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0,
        altitudeM = if (hasAltitude()) altitude else 0.0,
    )
}
