@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rodalog.R
import app.rodalog.data.Trip
import app.rodalog.trip.Phase
import app.rodalog.trip.TripBus
import app.rodalog.trip.TripService
import app.rodalog.util.Fmt

@Composable
fun TripsScreen(vm: AppViewModel, host: Host) {
    val v by vm.current.collectAsState()
    val s by vm.settings.collectAsState()
    val trips by vm.trips.collectAsState()
    val live by TripBus.state.collectAsState()
    val fmt = remember(s) { Fmt(s) }
    val ctx = LocalContext.current
    val veh = v
    var editing by remember { mutableStateOf<Trip?>(null) }

    // lee permTick para refrescar tras conceder permisos
    @Suppress("UNUSED_VARIABLE") val tick = host.permTick

    if (veh == null) {
        EmptyState(Icons.Filled.Speed, stringResource(R.string.need_vehicle_first))
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card2(Modifier.fillMaxWidth()) {
                val active = live.phase != Phase.IDLE
                Text(
                    stringResource(
                        when (live.phase) {
                            Phase.IDLE -> R.string.trip_computer
                            Phase.ARMED -> R.string.trip_waiting
                            Phase.RECORDING -> R.string.trip_recording
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (live.phase == Phase.RECORDING) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmt.num(fmt.speedVal(live.speedKmh), 0),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(fmt.speedUnit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.distance), fmt.dist(live.distanceKm, 2), Modifier.weight(1f))
                    StatTile(stringResource(R.string.time), fmt.duration(live.elapsedSec), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.avg_speed), fmt.speed(live.avgSpeedKmh), Modifier.weight(1f))
                    StatTile(stringResource(R.string.max_speed), fmt.speed(live.maxSpeedKmh), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        stringResource(R.string.est_consumption),
                        if (active) fmt.cons(live.consumptionL100) else "—",
                        Modifier.weight(1f),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    StatTile(
                        stringResource(R.string.fuel_used),
                        if (active) fmt.vol(live.fuelUsedL, 2) else "—",
                        Modifier.weight(1f),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        stringResource(R.string.fuel_left),
                        fmt.vol(if (active) live.fuelLeftL else veh.fuelLevelL, 1),
                        Modifier.weight(1f),
                    )
                    StatTile(
                        stringResource(R.string.range),
                        fmt.dist(if (active) live.rangeKm else rangeOf(veh.fuelLevelL, live.consumptionL100, veh.baseConsumption), 0),
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatTile(
                    stringResource(R.string.odometer),
                    fmt.dist(if (active) live.odometerKm else veh.odometerKm, 1),
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (active) {
                    Button(
                        onClick = { TripService.stop(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.finish_trip))
                    }
                } else {
                    Button(
                        onClick = {
                            if (!app.rodalog.ui.Perms.fine(ctx)) host.requestBase() else TripService.start(ctx, veh.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.start_trip))
                    }
                    if (veh.btAddress.isBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.hint_link_bt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (active && live.accuracyM >= 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.gps_accuracy, fmt.num(live.accuracyM.toDouble(), 0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.history)) }
        if (trips.isEmpty()) {
            item { Text(stringResource(R.string.no_trips), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(trips, key = { it.id }) { t ->
            Card2(Modifier.fillMaxWidth(), onClick = { editing = t }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt.dateTime(t.startTime), fontWeight = FontWeight.Bold)
                    Text(fmt.dist(t.distanceKm, 1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${fmt.duration(t.endTime / 1000 - t.startTime / 1000)} · ${fmt.speed(t.avgSpeedKmh)} · ${fmt.vol(t.fuelUsedL, 2)} · ${stringResource(if (t.category == "WORK") R.string.work else R.string.personal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    val e = editing
    if (e != null) {
        TripDialog(
            t = e,
            fmt = fmt,
            onDismiss = { editing = null },
            onSave = {
                vm.saveTrip(it)
                editing = null
            },
            onDelete = {
                vm.deleteTrip(e)
                editing = null
            },
        )
    }
}

private fun rangeOf(levelL: Double, liveCons: Double, base: Double): Double {
    val c = if (liveCons > 0) liveCons else if (base > 0) base else 8.0
    return levelL / c * 100.0
}

@Composable
private fun TripDialog(t: Trip, fmt: Fmt, onDismiss: () -> Unit, onSave: (Trip) -> Unit, onDelete: () -> Unit) {
    var category by remember { mutableStateOf(t.category) }
    var notes by remember { mutableStateOf(t.notes) }
    FormDialog(
        title = fmt.dateTime(t.startTime),
        onDismiss = onDismiss,
        onSave = { onSave(t.copy(category = category, notes = notes)) },
        onDelete = onDelete,
    ) {
        if (t.route.isNotBlank()) RouteSketch(t.route)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.distance), fmt.dist(t.distanceKm, 2), Modifier.weight(1f))
            StatTile(stringResource(R.string.time), fmt.duration(t.movingSec), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.max_speed), fmt.speed(t.maxSpeedKmh), Modifier.weight(1f))
            StatTile(stringResource(R.string.est_consumption), fmt.cons(t.consumptionL100), Modifier.weight(1f))
        }
        Text(
            "${fmt.dist(t.startOdometerKm, 1)} → ${fmt.dist(t.endOdometerKm, 1)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Choice(
            listOf("PERSONAL" to stringResource(R.string.personal), "WORK" to stringResource(R.string.work)),
            category,
        ) { category = it }
        TextF(notes, { notes = it }, stringResource(R.string.notes), single = false)
    }
}
