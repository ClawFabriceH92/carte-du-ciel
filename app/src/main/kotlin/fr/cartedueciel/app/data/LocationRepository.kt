package fr.cartedueciel.app.data

import fr.cartedueciel.astro.sky.GeoPosition
import fr.cartedueciel.sensors.CoarseLocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LocationSource { DEVICE, MANUAL, DEFAULT }

/** Lieu courant et sa provenance. */
data class LocationState(val position: GeoPosition, val source: LocationSource, val name: String?)

/**
 * Détermine le lieu de l'observateur : lieu manuel si choisi, sinon position du téléphone
 * (dernière connue puis demande unique), sinon lieu par défaut (Paris) avec avertissement.
 */
class LocationRepository(private val provider: CoarseLocationProvider) {

    private val _state = MutableStateFlow(LocationState(GeoPosition.PARIS, LocationSource.DEFAULT, "Paris"))
    val state: StateFlow<LocationState> = _state

    val hasPermission: Boolean get() = provider.hasPermission

    /** Applique les réglages ; en mode automatique, interroge le téléphone. */
    suspend fun refresh(settings: SkySettings) {
        if (settings.locationMode == LocationMode.MANUAL) {
            _state.value = LocationState(
                GeoPosition(settings.manualLatitude.coerceIn(-90.0, 90.0), settings.manualLongitude.coerceIn(-180.0, 180.0)),
                LocationSource.MANUAL,
                settings.manualPlaceName,
            )
            return
        }
        if (!provider.hasPermission) {
            if (_state.value.source != LocationSource.DEFAULT) _state.value = LocationState(GeoPosition.PARIS, LocationSource.DEFAULT, "Paris")
            return
        }
        provider.lastKnown()?.let { _state.value = LocationState(it, LocationSource.DEVICE, null) }
        provider.requestOnce()?.let { _state.value = LocationState(it, LocationSource.DEVICE, null) }
    }
}
