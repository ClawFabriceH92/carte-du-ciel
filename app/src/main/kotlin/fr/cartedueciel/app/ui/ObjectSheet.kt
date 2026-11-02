package fr.cartedueciel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cartedueciel.app.R
import fr.cartedueciel.app.SkyViewModel
import fr.cartedueciel.app.engine.ObjectDetails
import fr.cartedueciel.app.engine.ObjectType
import fr.cartedueciel.astro.sky.SolarBody
import fr.cartedueciel.app.engine.SkyTarget

/** Fiche objet (panneau glissant depuis le bas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectSheet(viewModel: SkyViewModel, details: ObjectDetails) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = viewModel::dismissSheet, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(details.title, style = MaterialTheme.typography.headlineSmall)
            details.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val typeText = when (details.type) {
                ObjectType.STAR -> stringResource(R.string.object_type_star)
                ObjectType.PLANET -> stringResource(R.string.object_type_planet)
                ObjectType.SUN -> stringResource(R.string.object_type_sun)
                ObjectType.MOON -> stringResource(R.string.object_type_moon)
                ObjectType.CONSTELLATION -> stringResource(R.string.object_type_constellation)
            }
            Text(typeText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            details.constellationName?.let { InfoRow(stringResource(R.string.object_constellation), it) }
            details.magnitude?.let { InfoRow(stringResource(R.string.object_magnitude), Formatters.magnitude(it)) }
            when {
                details.distancePc != null -> Formatters.starDistance(context, details.distancePc, settings.distanceUnit)?.let { InfoRow(stringResource(R.string.object_distance), it) }
                details.distanceAu != null -> InfoRow(stringResource(R.string.object_distance), Formatters.bodyDistance(context, details.distanceAu, details.type == ObjectType.MOON))
            }
            if (details.moonPhase != null && details.phaseFraction != null) {
                val phaseName = if (java.util.Locale.getDefault().language == "fr") details.moonPhase.fr else details.moonPhase.en
                InfoRow(stringResource(R.string.object_phase), stringResource(R.string.object_phase_value, phaseName, (details.phaseFraction * 100).toInt()))
            }
            details.starCount?.let { InfoRow(stringResource(R.string.object_designation), stringResource(R.string.object_stars_count, it)) }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.object_position), style = MaterialTheme.typography.titleSmall)
            InfoRow(stringResource(R.string.object_altitude), Formatters.degrees(details.altitudeDeg) + if (details.altitudeDeg < 0) " (" + stringResource(R.string.below_horizon) + ")" else "")
            InfoRow(stringResource(R.string.object_azimuth), Formatters.degrees(details.azimuthDeg))

            details.events?.let { ev ->
                Spacer(Modifier.height(4.dp))
                when {
                    ev.circumpolar -> Text(stringResource(R.string.object_circumpolar), style = MaterialTheme.typography.bodyMedium)
                    ev.neverRises -> Text(stringResource(R.string.object_never_rises), style = MaterialTheme.typography.bodyMedium)
                }
                Formatters.time(context, ev.rise)?.let { InfoRow(stringResource(R.string.object_rise), it) }
                Formatters.time(context, ev.culmination)?.let { t ->
                    val alt = ev.culminationAltitudeDeg?.let { " (" + Formatters.degrees(it, 0) + ")" } ?: ""
                    InfoRow(stringResource(R.string.object_culmination), t + alt)
                }
                Formatters.time(context, ev.set)?.let { InfoRow(stringResource(R.string.object_set), it) }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.guideTo(details.target) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.object_guide_to))
            }
            if (details.target is SkyTarget.Body && (details.target.body == SolarBody.SUN)) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
