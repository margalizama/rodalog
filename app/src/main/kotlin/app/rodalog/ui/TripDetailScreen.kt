@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rodalog.R
import app.rodalog.util.Fmt
import app.rodalog.util.KM_PER_MI

@Composable
fun TripDetailScreen(vm: AppViewModel, tripId: String, onBack: () -> Unit) {
    val trips by vm.trips.collectAsState()
    val fuel by vm.fuel.collectAsState()
    val s by vm.settings.collectAsState()
    val fmt = remember(s) { Fmt(s) }
    val t = trips.firstOrNull { it.id == tripId }

    if (t == null) {
        EmptyState(Icons.Filled.Speed, stringResource(R.string.no_trips))
        return
    }

    val points = remember(t.route) { parseRoute(t.route) }
    var category by remember(t.id) { mutableStateOf(t.category) }
    var notes by remember(t.id) { mutableStateOf(t.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    val lastPrice = fuel.firstOrNull { it.liters > 0 }?.pricePerLiter ?: 0.0
    val cost = t.fuelUsedL * lastPrice
    val costPerKm = if (t.distanceKm > 0) cost / t.distanceKm else 0.0
    val costPerUnit = if (s.distanceUnit == "MI") costPerKm * KM_PER_MI else costPerKm
    val totalSec = ((t.endTime - t.startTime) / 1000).coerceAtLeast(0L)
    val stoppedSec = (totalSec - t.movingSec).coerceAtLeast(0L)
    val hasSpeed = points.any { it.kmh > 0 }

    Column(Modifier.fillMaxSize()) {
        // Mapa fijo arriba (fuera del scroll para poder moverlo con el dedo)
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            if (points.size >= 2) {
                TripMap(points, Modifier.fillMaxSize())
                Text(
                    stringResource(R.string.map_credit),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.White.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp),
                    fontSize = 10.sp,
                    color = Color.Black,
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_route), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(fmt.dateTime(t.startTime), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (hasSpeed) {
                SpeedLegend(fmt)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.distance), fmt.dist(t.distanceKm, 2), Modifier.weight(1f), big = true)
                StatTile(stringResource(R.string.time), fmt.duration(totalSec), Modifier.weight(1f), big = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.moving_time), fmt.duration(t.movingSec), Modifier.weight(1f))
                StatTile(stringResource(R.string.stopped_time), fmt.duration(stoppedSec), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.avg_speed), fmt.speed(t.avgSpeedKmh), Modifier.weight(1f))
                StatTile(stringResource(R.string.max_speed), fmt.speed(t.maxSpeedKmh), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    stringResource(R.string.est_consumption),
                    fmt.cons(t.consumptionL100),
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                StatTile(
                    stringResource(R.string.fuel_used),
                    fmt.vol(t.fuelUsedL, 2),
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    stringResource(R.string.trip_cost),
                    if (cost > 0) fmt.money(cost) else "—",
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                StatTile(
                    stringResource(R.string.cost_per_distance, fmt.distUnit),
                    if (costPerUnit > 0) fmt.money(costPerUnit) else "—",
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            StatTile(
                stringResource(R.string.odometer),
                "${fmt.dist(t.startOdometerKm, 1)} → ${fmt.dist(t.endOdometerKm, 1)}",
                Modifier.fillMaxWidth(),
            )

            if (hasSpeed && points.size >= 2) {
                Card2(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.speed_profile, fmt.speedUnit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LineChart(points.map { fmt.speedVal(it.kmh.toDouble()) }, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Choice(
                listOf("PERSONAL" to stringResource(R.string.personal), "WORK" to stringResource(R.string.work)),
                category,
            ) { category = it }
            TextF(notes, { notes = it }, stringResource(R.string.notes), single = false)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        vm.saveTrip(t.copy(category = category, notes = notes.trim()))
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_trip_q)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteTrip(t)
                    onBack()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SpeedLegend(fmt: Fmt) {
    val b1 = fmt.num(fmt.speedVal(30.0), 0)
    val b2 = fmt.num(fmt.speedVal(60.0), 0)
    val b3 = fmt.num(fmt.speedVal(90.0), 0)
    val labels = listOf("< $b1", "$b1–$b2", "$b2–$b3", "> $b3")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(SPEED_COLORS[i])))
                Spacer(Modifier.size(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(fmt.speedUnit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
